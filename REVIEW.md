# Review analysis — PR #39 "Multi-Dataspace Support"

https://github.com/SEAMWARE/fdsc-edc/pull/39 (`ticket-114/work` → `main`)

Reviewer: **Mortega5**, 2026-11-19, two `COMMENTED` reviews (no approval, no
change request), **6 inline comments, 0 issue-level comments, 0 replies**.
All comments are against commit `b5ecb8d`, which is still the branch head, so
none of them is stale.

Every claim below was checked against the working tree. Verdicts:
**confirmed** = reproduced in the code, **confirmed (empirically)** = also
executed, **valid question** = the code does not contain the answer, so the
reviewer is right that it is unproven.

## Status: all six addressed

| # | File | Topic | Severity | Verdict | Resolution |
|---|------|-------|----------|---------|------------|
| 6 | `FDSCTransferControlExtension.java` | APISIX admin token logged at INFO | **High** | Confirmed (empirically) | Fixed: `@JsonIgnore` + redacting `toString`, `ApisixTokenRedactionTest` |
| 1 | `ProfileTrustedIssuerIdExtractionFunction.java` | TIL outage surfaces as 5xx, no test | **Medium–High** | Confirmed | Fixed: unreachable TIL rejects (401); 4 cases in `ProfileTrustCheckingContextRegistryTest` |
| 2 | `FDSCDcpEndpointDataReferenceService.java` | Cross-dataspace EDR isolation unproven | **Medium** | Valid question | Documented as ADR-13 + `EdrIsRejectedByForeignRouteTest` |
| 3 | `ProductCatalogApiClient.java` | Full catalog scan + new duplicate failure | **Medium** | Confirmed (both asks) | Fixed: early exit at the second match + release-notes section 5 |
| 5 | `docker/multi-dataspace/generate-jwks.sh` | `chmod 644` on a private key | **Low** | Confirmed | Fixed: test-only comment |
| 4 | `TMFBackedContractNegotiationStore.java` | Missing comment on skipped filter | **Low** | Confirmed, documented elsewhere | Fixed: comment at both call sites |

Nothing here blocked the design. Verification after the changes: full suite
green (`mvn clean test`), `mvn spotless:check` green,
`./scripts/verify-tck-config.sh` green.

Two findings were proven rather than argued: the token leak was reproduced by
serializing an identical record with the project's Jackson, and the TIL fix was
confirmed by stashing it and watching the new test error with the very
`BadGatewayException` the reviewer predicted.

One claim in the original analysis changed on contact with the stack: the
negative EDR check could not go into `verify-multi-dataspace.sh`, because that
compose stack carries no APISIX and runs with `fdscTransfer.enabled=false`. It
became a unit test that applies both of the gateway's accept conditions
instead, and ADR-13 says so explicitly rather than implying coverage that does
not exist.

---

## 1 — `ProfileTrustedIssuerIdExtractionFunction`: a TIL outage becomes a 5xx

**Claim.** `profileIssuers.getSupportedTypes(...)` can throw; the profile
registry is a bare `EbsiTrustedIssuersRegistry(profileClient)`; the exception
reaches the DSP endpoint as a 5xx instead of a 401, so one dataspace's TIL
outage looks like a connector fault to that dataspace's peers.

**Verdict: confirmed.**

- `TirExtension.java:85-90` builds both the global and each profile registry with
  the **single-argument** constructor.
- That constructor is `this(tirClient, List.of(), null)` —
  `EbsiTrustedIssuersRegistry.java:75-77` — so `additionalClients` is empty and
  `monitor` is `null`.
- `getSupportedTypes` (`:109-119`) calls `tirClient.getIssuer(...)` **directly**,
  outside `lookupSkippingFailure`. The cooldown and the catch only ever apply to
  `additionalClients`, which for a profile registry is always empty.
- `TirClient.getIssuer` throws `BadGatewayException` on any non-404 response and
  on `IOException`.
- `isTrusted` does not catch, and `apply` does not catch.
- Disassembling `ProtocolTokenValidatorImpl` from
  `control-plane-aggregate-services-0.18.1.jar` shows no exception table around
  the `ParticipantIdExtractionFunction.apply` call, so the exception does
  propagate as the reviewer describes.

**Also confirmed:** `ProfileTrustCheckingContextRegistryTest` has four test
methods (`participantIdOnlyForIssuersTrustedInTheProfile`,
`defaultProfileOnlyTrustsTheGlobalTil`, `credentialOfUntrustedTypeIsRejected`,
`everyProfileIsWrapped`) and none of them stubs a throwing TIL.

**Caveat the reviewer could not see.** The suggested reuse of
`lookupSkippingFailure` is not a drop-in: it calls `monitor.warning(...)`, and
the profile registries are constructed with `monitor == null`. Routing the
primary client through it without also passing a monitor at
`TirExtension.java:85-90` turns the TIL outage into an NPE — the same 5xx by
another route.

**Recommended action.** Catch `RuntimeException` in `isTrusted`, log, and return
`false` so the request is rejected as 401; pass `monitor` into the profile
registries if the cooldown is reused. Add a parameterized case to
`ProfileTrustCheckingContextRegistryTest` where `tilA.getIssuer` throws and the
expected participant id is `null`. The design doc already promises this
behaviour (`docs/multi-dataspace-design.md:360`: "A failing or hanging profile
TIL is skipped — its issuers count as untrusted"), so the code currently
contradicts its own ADR-9 for the primary client. That makes this a
documentation/implementation mismatch, not just a robustness nit.

## 2 — EDR issuer: where is `iss` enforced?

**Claim.** All contexts sign with the same `KEY_NAME` and the same JWKS, so the
only per-context part of the EDR token is `iss`/`aud`. Isolation therefore rests
on APISIX checking `iss` against the discovery document of the route, and
nothing in the repo proves the gateway does.

**Verdict: valid question — the premise is confirmed, the conclusion is
overstated.**

Confirmed premise:

- `FDSCDcpEndpointDataReferenceService:89` resolves the single shared
  `KEY_NAME`; `JwksController:67` serves that same key for every context.
- `OidConfigController:105-115` differs per context in `issuer` only; `jwks_uri`
  is one shared value, as its own Javadoc states ("All contexts share one JWKS").
- `EdrIssuerMatchesDiscoveryTest` asserts the EDC-side agreement between the two,
  not gateway behaviour. `verify-multi-dataspace.sh` says in its header that a
  full exchange is out of scope and lists no EDR check.

Where the conclusion is too strong: the route is not only bound to a discovery
document. `TransferMapper.java:105-111` sets
`required_scopes = [transferProcessId]` and `client_id = transferProcessId`, and
`FDSCDcpEndpointDataReferenceService` puts `scope = dataFlow.getId()` in the
token. So a token minted for transfer X is accepted only by the route for
transfer X — and a transfer belongs to exactly one dataspace. Cross-dataspace
replay would need the victim dataspace's own transfer token, which is not a
cross-dataspace leak. The `iss` check is a second line of defence, not the only
one.

**Recommended action.** Two cheap steps, in this order:

1. Reply with the scope argument above and add it to
   `docs/multi-dataspace-design.md` next to the EDR section — it is the real
   isolation mechanism and is currently nowhere written down.
2. Take the reviewer's alternative anyway: mint an EDR in context A and assert a
   context B route rejects it, in `verify-multi-dataspace.sh`. It pins both
   layers and costs one more case in a script that already builds tokens.

Worth stating explicitly in the reply whether a shared signing key across
contexts is a deliberate decision (it reads like one — one JWKS endpoint, one
key rotation) or something to revisit later.

## 3 — `ProductCatalogApiClient.getByPolicyId`: full scan and a new hard failure

**Claim (two asks).** The loop no longer stops at the first match, so (a) a
duplicate policy id now throws where it was silently tolerated — mention it in
the release notes; (b) every policy resolution scans all pages — cache it, or at
least exit early once a second match is found.

**Verdict: both confirmed.**

- Old loop condition was `while (optionalPolicy.isEmpty() && posAvailable)`;
  the new one is `while (posAvailable)` (`ProductCatalogApiClient.java:142`).
- The duplicate now throws `DuplicateIdException` (`:163-176`). It extends
  `BadGatewayException`, so the Javadoc's `{@link BadGatewayException}` is
  accurate — not a mismatch.
- **The release-notes gap is real.** `releases/notes/RELEASE_NOTES_MULTI_DATASPACE.md`
  (106 lines) never mentions policy-id uniqueness or duplicates. The rule is
  documented only in `docs/multi-dataspace.md:342` ("Keeping ids unique"), which
  an upgrading operator has no reason to open. A catalog that negotiates fine
  today starts failing after the upgrade, with no warning in the notes.

On the early exit: it is correct and cheap, but note it trades away the error
message. The current exception names *every* colliding offering and its
contexts, which is exactly what an operator needs to fix the data. Breaking at
`matches.size() > 1` keeps two of them — still enough to act on. Recommend the
early exit, not a cache: a cache adds invalidation questions on a catalog that
operators edit through TMForum.

**Recommended action.** Add a "Breaking / data requirements" bullet to the
release notes pointing at `docs/multi-dataspace.md#keeping-ids-unique`, and
break out of the page loop once a second match is found.

## 4 — `TMFBackedContractNegotiationStore:277`: skipped server-side filter

**Claim.** For the default context the server-side filter is skipped and all
quotes/agreements of all dataspaces are paged through; no leak, same cost as
`main`, but a one-line comment would help the next reader.

**Verdict: confirmed, and the rationale already exists — one level down.**

`ContextCriteria.serverSideContext` carries it in its own Javadoc: "Empty for no
context and for the default one, which has to be fetched unfiltered so its
untagged legacy entities are kept (ADR-3)". The analysis of "no leak" is also
right: `filterPredicate` is built from all criteria including the context one and
is applied to every mapped agreement.

So this is the weakest of the six — the information is there, just not at the
call site. **Recommended action:** add the one-line comment anyway (it is free)
and reply pointing at the helper's Javadoc so the reviewer knows the reasoning
was not missing, only relocated.

## 5 — `generate-jwks.sh:40`: `chmod 644` on a private key

**Claim.** Fine for the compose check, but add a comment saying it is test-only
so it is not copied into a real deployment.

**Verdict: confirmed, trivial.** The existing comment explains the *mechanism*
("Readable by the verifier container, whatever user it runs as") but not that it
is acceptable only here. The file header says "nginx entrypoint hook", which
does not read as test-only either.

**Recommended action.** Accept verbatim. One comment line, e.g. *"Test-only: a
world-readable private key is acceptable only in this throwaway compose stack —
never do this in a deployment."*

## 6 — APISIX admin token in the startup log

**Claim.** `initialize` logs `new ObjectMapper().writeValueAsString(transferConfig)`
at INFO; `TransferConfig.Apisix` is a record with a `token` component and no
`@JsonIgnore`, so the admin token lands in the startup log. Reviewer explicitly
did not run it and asked for confirmation.

**Verdict: confirmed, empirically. The reviewer is right.**

- `FDSCTransferControlExtension.java:150` is the log line.
- `TransferConfig.java:161` — `public record Apisix(String address, String token,
  String httpsProxy)`, no `@JsonIgnore` anywhere in the file.
- Executed against the project's Jackson (2.19.2), a plain `ObjectMapper` on an
  identical record produces:
  `{"address":"http://apisix:9180","token":"SUPER-SECRET-ADMIN-TOKEN","httpsProxy":null}`

So the APISIX admin token — which grants write access to the gateway's admin API —
is written to stdout at INFO on every boot, where it reaches whatever ships the
container logs.

Also confirmed: the line is pre-existing (`git show origin/main:...` → line 147),
so this PR did not introduce it. The reviewer's reasoning for fixing it here
still holds: the PR reworks this extension, and the DCP image now serves several
dataspaces from one runtime, which widens the blast radius of that one token.

`token` is the only secret-bearing component in `TransferConfig`; `Oid4Vc`,
`OidConfig` and `Dcp` hold hosts and paths only. So the fix is contained.

**Recommended action.** Highest-value item in this review. Put `@JsonIgnore` on
the `token` component of `Apisix` (`@JsonIgnore` on a record component needs
`@JsonIgnore` with the component declaration, verify the serialized output in a
test) and add a small unit test asserting the serialized config does not contain
the token value. Reply to the reviewer with the confirmed output above, since
they asked to have it verified before acting.

---

## Suggested handling order

1. **#6** — secret in logs. Smallest change, largest consequence. Fix + test.
2. **#1** — fix `isTrusted`, wire the monitor, add the TIL-down test. Closes a
   gap between ADR-9 and the code.
3. **#3** — release-notes bullet plus the early exit.
4. **#5** — one comment line.
5. **#4** — one comment line plus a reply pointing at the existing Javadoc.
6. **#2** — reply first (the per-transfer scope argument), then decide whether to
   add the negative EDR check to `verify-multi-dataspace.sh`. The only item that
   warrants discussion rather than a patch.

Items 1, 3, 5 and 6 are accept-as-written. Item 4 is accept-with-a-note. Item 2
is the one where the reply changes the conclusion.
