# Observability

The controlplane images (`controlplane-oid4vc` and `controlplane-dcp`) export **audit logs** and
**business metrics** over OTLP to an OpenTelemetry Collector. This document covers what to
configure when deploying the connector and how to check that the telemetry arrives.

| Module                    | What it does                                                                 |
|---------------------------|------------------------------------------------------------------------------|
| `otel-observability-core` | Sets up the OTLP exporters (logs + metrics) shared by the other two modules  |
| `otel-audit-extension`    | Audit log records for contract negotiations, transfer processes and catalog changes made in TMForum |
| `otel-business-metrics`   | Counters and histograms for negotiation and transfer lifecycles              |

All three modules ship in both images and are **enabled by default**. They only observe EDC
events: they never read or write the stores, and a telemetry failure never breaks a negotiation
or a transfer.

> This is separate from the OpenTelemetry Java agent. These extensions build their own
> OpenTelemetry SDK and do not need the agent.

## Contents

1. [Setting configuration values](#1-setting-configuration-values)
2. [OTLP export](#2-otlp-export)
3. [Negotiation and transfer audit](#3-negotiation-and-transfer-audit)
4. [Catalog audit via the TMForum hub](#4-catalog-audit-via-the-tmforum-hub)
5. [Business metrics](#5-business-metrics)
6. [Verifying that it works](#6-verifying-that-it-works)
7. [Troubleshooting](#7-troubleshooting)
8. [Known limitations](#8-known-limitations)

## 1. Setting configuration values

Settings are normal EDC configuration keys. EDC reads them from the properties file
(`edc.fs.config`), from JVM system properties (`-Dkey=value`) and from environment variables.

EDC maps an environment variable to a key by lowercasing it and replacing `_` with `.`. For example,
`OTEL_EXPORTER_OTLP_ENDPOINT` becomes `otel.exporter.otlp.endpoint`.

> **Keys that contain a hyphen cannot be set with an environment variable.**
> `EDC_OTEL_AUDIT_STATE_CACHE_SIZE` becomes `edc.otel.audit.state.cache.size`, not
> `edc.otel.audit.state-cache.size`, so EDC ignores it without any warning. Set these keys in the
> properties file or as `-D` system properties (for example through `JAVA_TOOL_OPTIONS`):
> `state-cache.size`, `catalog.callback-url`, `catalog.retry-interval`, `catalog.dedup-cache.size`,
> `reason.max-distinct`.
> You only need them to change the defaults, which work for a normal deployment.

## 2. OTLP export

Module `otel-observability-core`. Logs and metrics go to an OTLP endpoint, `http/protobuf` by
default.

### Settings

| Key | Env variable | Default | Description |
|-----|--------------|---------|-------------|
| `edc.otel.logs.endpoint` | `EDC_OTEL_LOGS_ENDPOINT` | — | Full logs endpoint, used as is. Overrides everything below |
| `edc.otel.metrics.endpoint` | `EDC_OTEL_METRICS_ENDPOINT` | — | Full metrics endpoint, used as is. Overrides everything below |
| `otel.exporter.otlp.endpoint` | `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4318` (`:4317` for gRPC) | Base endpoint for both signals |
| `otel.exporter.otlp.logs.endpoint` | `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` | — | Full logs endpoint, used as is |
| `otel.exporter.otlp.metrics.endpoint` | `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` | — | Full metrics endpoint, used as is |
| `otel.exporter.otlp.protocol` | `OTEL_EXPORTER_OTLP_PROTOCOL` | `http/protobuf` | `http/protobuf` or `grpc`. Any other value stops the connector at startup |
| `otel.service.name` | `OTEL_SERVICE_NAME` | `fdsc-edc` | `service.name` resource attribute |
| `otel.resource.attributes` | `OTEL_RESOURCE_ATTRIBUTES` | — | Extra resource attributes, `key=value,key=value` |

### Endpoint precedence

For each signal (logs, metrics), the first value that is set wins:

1. `edc.otel.logs.endpoint` / `edc.otel.metrics.endpoint`
2. `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` / `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`
3. `OTEL_EXPORTER_OTLP_ENDPOINT` plus the signal path (`/v1/logs`, `/v1/metrics`). With `grpc`, no
   path is added
4. Default: `http://otel-collector:4318` (`http://otel-collector:4317` with `grpc`), plus the signal
   path for HTTP

Steps 1 and 2 use the URL exactly as given, so it must include `/v1/logs` or `/v1/metrics` when
the protocol is HTTP.

For `service.name`, `OTEL_SERVICE_NAME` overrides a `service.name=` entry in
`OTEL_RESOURCE_ATTRIBUTES`.

### Ignored variables

`OTEL_LOGS_EXPORTER`, `OTEL_METRICS_EXPORTER`, `OTEL_TRACES_EXPORTER` and the other OpenTelemetry
SDK autoconfigure variables are **ignored on purpose**. They only configure the OpenTelemetry Java
agent, not these extensions. For example, `OTEL_METRICS_EXPORTER=none` does **not** turn off the
business metrics: use `edc.otel.metrics.business.enabled=false` for that (see sections 3–5).

### Typical deployment

Only the standard variables are needed:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.monitoring:4318
OTEL_SERVICE_NAME=fdsc-edc-provider
OTEL_RESOURCE_ATTRIBUTES=deployment.environment=prod,participant.id=did:web:example.org
```

At startup the connector logs the configuration it resolved. Check this line first:

```
Otel Core: exporting as service 'fdsc-edc-provider' over HTTP_PROTOBUF, logs to http://otel-collector.monitoring:4318/v1/logs, metrics to http://otel-collector.monitoring:4318/v1/metrics
```

## 3. Negotiation and transfer audit

Module `otel-audit-extension`. Every contract negotiation and transfer process state change becomes
one OTLP log record. The body is `Audit: <EventType>` (for example
`Audit: ContractNegotiationFinalized`). Severity is `WARN` for `TERMINATED` and `INFO` for every
other state.

### Settings

| Key | Default | Description |
|-----|---------|-------------|
| `edc.otel.audit.enabled` (env `EDC_OTEL_AUDIT_ENABLED`) | `true` | Turns negotiation/transfer audit on or off. It does not affect catalog audit (section 4) |
| `edc.otel.audit.state-cache.size` | `10000` | Maximum number of entities whose last state is kept in memory, used to fill in `audit.state.previous` |

### Attributes

Attribute names match the audit logs of `participant-edc-connector`, so the same queries work for
both connectors in a shared backend. The `audit.*`, `event.id` and `offer.id` attributes are added
by fdsc-edc.

| Attribute | Present on | Value |
|-----------|-----------|-------|
| `event.type` | all | EDC event class, e.g. `ContractNegotiationRequested`, `TransferProcessStarted` |
| `event.id` | all | EDC event envelope id |
| `audit.domain` | all | `contract_negotiation` or `transfer_process` |
| `audit.state.new` | all | New state, e.g. `REQUESTED`, `FINALIZED`, `PROVISIONING_REQUESTED` |
| `audit.state.previous` | all except the first transition | Previous state seen by this process (see [limitations](#8-known-limitations)) |
| `negotiation.id` | negotiation | Contract negotiation id |
| `participant.counterparty.id` | negotiation | Counterparty participant id |
| `participant.counterparty.address` | negotiation | Counterparty DSP address |
| `protocol` | negotiation | DSP protocol version |
| `offer.id` | negotiation | Id of the last contract offer |
| `asset.id` | negotiation, transfer | Asset id |
| `contract.id`, `contract.agreement.id` | negotiation `FINALIZED`, transfer | Agreement id (EDC 0.14.1 has one id, used for both attributes) |
| `participant.provider`, `participant.consumer` | negotiation `FINALIZED` | Agreement parties |
| `contract.signing.date` | negotiation `FINALIZED` | Agreement signing date (epoch seconds) |
| `transfer.id` | transfer | Transfer process id |
| `transfer.type` | transfer | Transfer type, `UNKNOWN` if it is not set |
| `transfer.data.destination.type` | transfer `STARTED` | Data address type, `NOT_SPECIFIED` if it is not set |
| `transfer.reason` | transfer `TERMINATED` / `SUSPENDED` | Reason given by EDC |

## 4. Catalog audit via the TMForum hub

Module `otel-audit-extension`. In FDSC, catalog entities live in TMForum and are created and
changed by the Marketplace, not through the EDC management API. To audit those changes, the
connector subscribes to the TMForum Product Catalog (TMF620) notification hub. TMForum then calls
the connector back for each change, and the connector turns each call into an audit log record.

| TMForum entity | `audit.domain` | Notification types the connector subscribes to |
|----------------|----------------|-------------------------------------------------|
| `ProductSpecification` (EDC asset) | `asset` | `…CreateEvent`, `…AttributeValueChangeEvent`, `…StateChangeEvent` |
| `ProductOffering` (EDC contract definition) | `contract_definition` | `…CreateEvent`, `…AttributeValueChangeEvent`, `…StateChangeEvent` |

### Settings

| Key | Default | Description |
|-----|---------|-------------|
| `edc.otel.audit.catalog.enabled` (env `EDC_OTEL_AUDIT_CATALOG_ENABLED`) | `true` | Turns catalog audit on or off. Independent of `edc.otel.audit.enabled` |
| `edc.otel.audit.catalog.callback-url` | computed | URL that TMForum calls. If it is not set, it is `http://<edc.hostname>:<web.http.tmfevents.port><web.http.tmfevents.path>` |
| `web.http.tmfevents.port` (env `WEB_HTTP_TMFEVENTS_PORT`) | `8688` | Port where the connector listens for TMForum callbacks |
| `web.http.tmfevents.path` (env `WEB_HTTP_TMFEVENTS_PATH`) | `/tmf-events` | Path where the connector listens for TMForum callbacks |
| `edc.otel.audit.catalog.retry-interval` | `30` | **Seconds** between attempts when a hub subscription fails |
| `edc.otel.audit.catalog.dedup-cache.size` | `10000` | Number of TMForum events remembered in memory, used to drop duplicate deliveries |

With the defaults and `edc.hostname=fdsc-edc`, the callback is `http://fdsc-edc:8688/tmf-events`.

### Prerequisites

1. **TMForum extension enabled.** The connector needs `tmfExtension.enabled=true` and
   `tmfExtension.productCatalogApi` set. The hub is `<productCatalogApi>/hub`. If either is
   missing, catalog audit is skipped and the connector logs one line, without failing:
   ```
   Otel Audit: catalog audit skipped, it requires tmfExtension.enabled=true
   Otel Audit: catalog audit skipped, tmfExtension.productCatalogApi not set
   ```
2. **TMForum can reach the connector at the callback URL.** The `tmfevents` port (default `8688`)
   must be exposed by the connector's Service/container. No NetworkPolicy, firewall or ingress rule
   may block traffic from TMForum to that host and port. This was checked in production
   (SEAMWARE thread, 2026-10-06).
3. **TMForum version.** Use **≥ 1.10.1**. Older versions accept the subscription but never send
   notifications. Production runs **1.14.1**, which is known to work. **Avoid 1.18.x**: it has a
   broken duplicate check on subscriptions (409) and does not work.

No other step is needed. At startup the connector registers the **6 hub subscriptions** on its
own. It retries every `retry-interval` seconds until TMForum accepts each one. If a subscription
already exists from an earlier start (TMForum answers 409), the connector takes it as done.

Expected startup log:

```
Otel Audit: catalog audit via TMForum notifications, callback http://fdsc-edc:8688/tmf-events
Otel Audit: subscribed to TMForum ProductSpecificationCreateEvent
...                                  (or: TMForum subscription already exists for ...)
```

### Attributes

Body: `Audit: <Domain><Action>`, e.g. `Audit: AssetCreated`, `Audit: ContractDefinitionUpdated`,
`Audit: AssetStateChanged`. Severity `INFO`. The record timestamp is the TMForum `eventTime`.

| Attribute | Value |
|-----------|-------|
| `event.type` | `AssetCreated`, `AssetUpdated`, `AssetStateChanged`, `ContractDefinitionCreated`, … |
| `event.id` | TMForum `eventId`, if TMForum sends it |
| `audit.domain` | `asset` or `contract_definition` |
| `audit.action` | `created`, `updated` or `state_changed` |
| `audit.source` | always `tmforum` |
| `tmf.event.type` | Original TMForum notification type, e.g. `ProductOfferingStateChangeEvent` |
| `tmf.id` | TMForum entity id (`urn:ngsi-ld:product-…`) |
| `tmf.lifecycle_status` | `lifecycleStatus` of the entity, e.g. `Active`, `retired` |
| `catalog.asset.id` | EDC asset id (the `externalId` of the ProductSpecification) |
| `catalog.definition.id` | EDC contract definition id (the `externalId` of the ProductOffering) |
| `tmf.product_specification.id` | Contract definition only: the ProductSpecification it points to |
| `catalog.policy.id`, `catalog.access.policy.id` | Contract definition only: ODRL `uid` of the contract and access policies |

A single TMForum change can produce more than one notification. For example, retiring an entity
sends both an `AttributeValueChangeEvent` and a `StateChangeEvent`, so it gives two records.

## 5. Business metrics

Module `otel-business-metrics`. The metrics are exported every 60 s, the default of the
OpenTelemetry SDK periodic reader.

### Settings

| Key | Default | Description |
|-----|---------|-------------|
| `edc.otel.metrics.business.enabled` (env `EDC_OTEL_METRICS_BUSINESS_ENABLED`) | `true` | Turns business metrics on or off |
| `edc.otel.metrics.business.state-cache.size` | `10000` | Maximum number of in-flight entities tracked per domain to compute durations |
| `edc.otel.metrics.business.reason.max-distinct` | `50` | Maximum number of distinct `reason` values before new ones are grouped as `other` |

### Metrics

| Metric | Type | Unit | Attributes | Meaning |
|--------|------|------|------------|---------|
| `contract_negotiation.transitions` | counter | `{transition}` | `state` | One per negotiation state change |
| `contract_negotiation.duration` | histogram | `s` | `state` (`FINALIZED` / `TERMINATED`) | From the first state (`INITIATED`, `REQUESTED` or `OFFERED`) to the final state |
| `transfer_process.transitions` | counter | `{transition}` | `state`, `type` | One per transfer state change |
| `transfer_process.duration` | histogram | `s` | `type` | From `INITIATED` to `DEPROVISIONED` |
| `transfer_process.interruptions` | counter | `{transition}` | `state` (`TERMINATED` / `SUSPENDED`), `reason`, `type` | Terminations and suspensions, by reason |

Histogram buckets (seconds): `0.1, 0.5, 1, 2.5, 5, 10, 30, 60, 120, 300, 600, 1800, 3600`.

Notes:

- A duration is only recorded when the same process saw the entity from its first state.
  Negotiations and transfers that were already in progress when the connector (re)started are
  counted in `transitions` but not in `duration`.
- `reason` is cut to 128 characters. An empty reason becomes `unspecified`. After
  `reason.max-distinct` different values, new reasons are reported as `other`. This keeps the
  number of series under control, because EDC reasons are free text and often contain ids.
- Backends such as Prometheus may rename the metrics (for example
  `contract_negotiation_transitions_total`, `transfer_process_duration_seconds_bucket`).

## 6. Verifying that it works

### Local stack

`docker-compose.tck.yml` includes an `otel-collector` service (config:
[`config/tck/otel-collector.yaml`](config/tck/otel-collector.yaml)). It receives OTLP/HTTP on
`4318` and prints everything with the `debug` exporter: log records in full (`verbosity: detailed`),
metrics as a summary (`verbosity: basic`). The connector uses its default endpoint
(`http://otel-collector:4318`), so no OTLP setting is needed.

```bash
# Start the stack. TMF_VERSION=1.14.1 is needed for catalog audit:
# the TCK default (1.5.4) does not send hub notifications
TMF_VERSION=1.14.1 docker compose -f docker-compose.tck.yml up --build -d edc

# Everything the collector received
docker compose -f docker-compose.tck.yml logs -f otel-collector
```

Useful filters on the collector output:

```bash
DC="docker compose -f docker-compose.tck.yml"

# Audit records (body "Audit: ...")
$DC logs otel-collector | grep 'Body: Str(Audit:'

# All records for one negotiation / transfer / asset
$DC logs otel-collector | grep -B25 -A5 'negotiation.id: Str(<id>)'
$DC logs otel-collector | grep -c 'catalog.asset.id: Str(<asset-id>)'

# Metrics (basic verbosity: one summary line per export, with the number of data points)
$DC logs otel-collector | grep -i 'metrics'

# Connector side: what the extensions logged at startup
$DC logs edc | grep -E 'Otel (Core|Audit|Metrics|Business)'
```

To see each metric data point with its attributes, set `debug/metrics` to `verbosity: detailed` in
`config/tck/otel-collector.yaml` and restart the collector.

Negotiation and transfer audit records and metrics appear as soon as negotiations or transfers
happen. For example, run the TCK (`./scripts/run-tck.sh --keep-containers`) and then look at the
collector logs.

### End-to-end catalog audit check: `scripts/verify-tmf-audit.sh`

This script checks the whole TMForum → connector → collector path:

1. Waits until the connector logs the 6 hub subscriptions (`subscribed to TMForum` or
   `subscription already exists`).
2. Creates, changes and retires a ProductSpecification and a ProductOffering directly in TMForum,
   with unique ids for each run.
3. Waits (up to 120 s) for the audit records in the collector output and checks:
   - 4 records for the asset and 4 for the contract definition (created, updated, and updated +
     state_changed when retired);
   - `catalog.policy.id` follows the policy change: 1 record with the first policy and 3 with the
     second one;
   - no record appears twice (the deduplication works).

```bash
TMF_VERSION=1.14.1 docker compose -f docker-compose.tck.yml up --build -d edc
./scripts/verify-tmf-audit.sh
```

It prints `OK`/`FAIL` for each check, ends with `Catalog audit verified.`, and exits with a non-zero
code on failure.

### In a real deployment

1. Connector logs: the `Otel Core: exporting as service …` line shows the right endpoints. For
   catalog audit, check the `callback …` line and the 6 `subscribed` / `already exists` lines. No
   `could not subscribe … retrying` line should keep repeating.
2. Collector: records with `service.name` = your `OTEL_SERVICE_NAME` and body `Audit: …` arrive
   after a negotiation, and metrics `contract_negotiation.*` / `transfer_process.*` arrive within
   about a minute.
3. Catalog: change an offering in the Marketplace (or with a `PATCH` on TMForum). An
   `Audit: ContractDefinitionUpdated` record should arrive within seconds.

## 7. Troubleshooting

### No audit logs arrive at the collector

| Check | How |
|-------|-----|
| Where the connector sends them | Startup line `Otel Core: exporting as service … logs to …`. Wrong host/port, or a missing `/v1/logs` on an explicit HTTP endpoint (`edc.otel.logs.endpoint`, `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT`) |
| Protocol mismatch | `grpc` against port 4318 or `http/protobuf` against 4317. Set `OTEL_EXPORTER_OTLP_PROTOCOL` to match the collector receiver |
| Audit turned off | `Otel Audit: negotiation/transfer audit disabled via edc.otel.audit.enabled` |
| Collector pipeline | The collector must have a `logs` pipeline with the `otlp` receiver |
| Network | The connector must reach the collector. Export errors appear in the connector logs as OpenTelemetry SDK warnings (`Failed to export logs…`) |
| No activity | Negotiation/transfer records appear only when a negotiation or transfer happens |
| Errors while building a record | `Otel Audit: failed to emit audit record for …`. The record is lost, the negotiation/transfer continues |

### No metrics arrive

- Same endpoint, protocol and network checks as above, using the `metrics to …` part of the
  startup line and a `metrics` pipeline in the collector.
- Metrics are exported every 60 s: wait at least one minute after the first negotiation.
- `Otel Metrics: disabled via edc.otel.metrics.business.enabled` means the metrics are turned off.
  Setting `OTEL_METRICS_EXPORTER=none` has **no** effect (see [ignored variables](#ignored-variables)).
- `duration` stays empty while `transitions` grows: no entity has reached its final state since
  the last restart, or the entities were already in progress at startup.

### Catalog audit receives no events

Most likely causes first:

1. **`tmfExtension.enabled` is not `true`**, or `tmfExtension.productCatalogApi` is not set. Look for
   `Otel Audit: catalog audit skipped, …` at startup.
2. **TMForum cannot reach the connector.** The subscriptions succeed (that is connector → TMForum),
   but the callbacks never arrive (TMForum → connector). Check that:
   - the callback URL in the startup line resolves **from inside TMForum's network** (`edc.hostname`
     must be the Service name, not `localhost`). If it does not, set
     `edc.otel.audit.catalog.callback-url`;
   - the `tmfevents` port (default 8688) is exposed by the container/Service;
   - no NetworkPolicy/firewall blocks it. Test it from the TMForum pod:
     `curl -i -X POST -H 'Content-Type: application/json' -d '{}' <callback-url>` must answer
     `204`.
3. **TMForum is older than 1.10.1.** Subscriptions are accepted but notifications are never sent.
   Upgrade (1.14.1 is known to work, avoid 1.18.x).
4. **Subscriptions never complete.** `could not subscribe to TMForum … retrying in 30s` keeps
   repeating: the hub (`<productCatalogApi>/hub`) cannot be reached or answers with an error.
5. **Catalog audit turned off**: `Otel Audit: catalog audit disabled via edc.otel.audit.catalog.enabled`.
6. **Callbacks arrive but nothing is exported.** Look for `Otel Audit: failed to audit TMForum catalog
   event` or `… without entity id`. The connector always answers `204` to TMForum, so TMForum
   shows no error even in this case.

## 8. Known limitations

- **Single replica only (deduplication is in memory).** The state cache of the negotiation/transfer
  audit, the catalog deduplication cache and the duration tracking of the metrics are all held in
  memory, in each process. With more than one controlplane replica:
  - the same catalog notification delivered again to a different replica is audited twice;
  - `audit.state.previous` may be missing or out of date when consecutive transitions of an entity
    run on different replicas;
  - durations are not recorded when an entity's first and final states are handled by different
    replicas.

  After a restart, the caches start empty: the first transition of each in-flight entity has no
  `audit.state.previous`.
- **Physical deletions in the catalog are not audited.** The connector does not subscribe to
  `…DeleteEvent`. This is on purpose: the Marketplace never deletes catalog entities, it retires
  them with `lifecycleStatus`, and that change is audited (`state_changed` / `updated`). An entity
  deleted directly in TMForum leaves no audit record.
- **Catalog changes made while the connector is down are not audited.** TMForum sends each
  notification only at the moment of the change. There is no catch-up when the connector starts
  again.
- **Hub subscriptions are never removed.** They stay in TMForum after shutdown, so later starts get
  a 409, which is expected. If the callback URL changes, the old subscriptions still point to the
  old URL until someone deletes them in TMForum (`DELETE <productCatalogApi>/hub/<id>`).
