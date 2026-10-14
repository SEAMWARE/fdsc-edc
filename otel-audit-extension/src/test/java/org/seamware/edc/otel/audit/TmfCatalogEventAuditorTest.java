/*
 * Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.seamware.edc.otel.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TmfCatalogEventAuditorTest {

  private static final String EVENT_TIME = "2026-10-06T10:00:00Z";
  private static final String SPEC_ID = "urn:ngsi-ld:product-specification:1";
  private static final String OFFERING_ID = "urn:ngsi-ld:product-offering:1";

  private InMemoryLogRecordExporter exporter;
  private TmfCatalogEventAuditor auditor;

  @BeforeEach
  void setUp() {
    exporter = InMemoryLogRecordExporter.create();
    var loggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
            .build();
    auditor =
        new TmfCatalogEventAuditor(
            loggerProvider.get("test"), mock(Monitor.class), Clock.systemUTC(), 100);
  }

  @Test
  void specificationCreationIsAuditedAsAsset() {
    auditor.audit(
        notification("ProductSpecificationCreateEvent", "productSpecification", spec("t1")));

    var record = single();
    assertEquals(
        TmfCatalogEventAuditor.DOMAIN_ASSET, attr(record, AuditEventSubscriber.ATTR_DOMAIN));
    assertEquals(
        TmfCatalogEventAuditor.ACTION_CREATED, attr(record, TmfCatalogEventAuditor.ATTR_ACTION));
    assertEquals("ASSET-1", attr(record, TmfCatalogEventAuditor.ATTR_ASSET_ID));
    assertEquals("AssetCreated", attr(record, AuditEventSubscriber.ATTR_EVENT_TYPE));
    assertEquals(
        "ProductSpecificationCreateEvent",
        attr(record, TmfCatalogEventAuditor.ATTR_TMF_EVENT_TYPE));
    assertEquals("Audit: AssetCreated", record.getBodyValue().asString());
    assertEquals(SPEC_ID, attr(record, TmfCatalogEventAuditor.ATTR_TMF_ID));
    assertEquals(
        TmfCatalogEventAuditor.SOURCE_TMFORUM, attr(record, TmfCatalogEventAuditor.ATTR_SOURCE));
    assertEquals("Active", attr(record, TmfCatalogEventAuditor.ATTR_LIFECYCLE_STATUS));
    assertEquals("evt-1", attr(record, AuditEventSubscriber.ATTR_EVENT_ID));
    assertEquals(
        Instant.parse(EVENT_TIME).toEpochMilli() * 1_000_000L, record.getTimestampEpochNanos());
  }

  @Test
  void offeringUpdateCarriesPolicyIds() {
    auditor.audit(
        notification(
            "ProductOfferingAttributeValueChangeEvent",
            "productOffering",
            offering("t1", "Active")));

    var record = single();
    assertEquals(
        TmfCatalogEventAuditor.DOMAIN_CONTRACT_DEFINITION,
        attr(record, AuditEventSubscriber.ATTR_DOMAIN));
    assertEquals(
        TmfCatalogEventAuditor.ACTION_UPDATED, attr(record, TmfCatalogEventAuditor.ATTR_ACTION));
    assertEquals("CD-1", attr(record, TmfCatalogEventAuditor.ATTR_DEFINITION_ID));
    assertEquals("ContractDefinitionUpdated", attr(record, AuditEventSubscriber.ATTR_EVENT_TYPE));
    assertEquals(SPEC_ID, attr(record, TmfCatalogEventAuditor.ATTR_TMF_SPECIFICATION_ID));
    assertEquals("P-CONTRACT", attr(record, TmfCatalogEventAuditor.ATTR_CONTRACT_POLICY_ID));
    assertEquals("P-ACCESS", attr(record, TmfCatalogEventAuditor.ATTR_ACCESS_POLICY_ID));
  }

  @Test
  void retirementIsAuditedAsStateChange() {
    auditor.audit(
        notification(
            "ProductOfferingStateChangeEvent", "productOffering", offering("t2", "retired")));

    var record = single();
    assertEquals(
        TmfCatalogEventAuditor.ACTION_STATE_CHANGED,
        attr(record, TmfCatalogEventAuditor.ATTR_ACTION));
    assertEquals("retired", attr(record, TmfCatalogEventAuditor.ATTR_LIFECYCLE_STATUS));
    assertEquals(
        "ContractDefinitionStateChanged", attr(record, AuditEventSubscriber.ATTR_EVENT_TYPE));
  }

  @Test
  void duplicatedDeliveryIsAuditedOnce() {
    var notification =
        notification("ProductSpecificationCreateEvent", "productSpecification", spec("t1"));

    auditor.audit(notification);
    auditor.audit(notification);

    assertEquals(1, exporter.getFinishedLogRecordItems().size());
  }

  @Test
  void newVersionOfTheSameEntityIsAuditedAgain() {
    auditor.audit(
        notification(
            "evt-1",
            "ProductSpecificationAttributeValueChangeEvent",
            "productSpecification",
            spec("t1")));
    auditor.audit(
        notification(
            "evt-2",
            "ProductSpecificationAttributeValueChangeEvent",
            "productSpecification",
            spec("t2")));

    assertEquals(2, exporter.getFinishedLogRecordItems().size());
  }

  @Test
  void unrelatedOrIncompleteEventsAreIgnored() {
    auditor.audit(
        notification("ProductOfferingPriceCreateEvent", "productOfferingPrice", spec("t1")));
    auditor.audit(notification("CatalogCreateEvent", "catalog", spec("t1")));
    auditor.audit(Map.of("event", Map.of()));
    auditor.audit(Map.of("eventType", "ProductSpecificationCreateEvent"));

    assertEquals(0, exporter.getFinishedLogRecordItems().size());
  }

  private static Map<String, Object> spec(String lastUpdate) {
    return Map.<String, Object>of(
        "id",
        SPEC_ID,
        "externalId",
        "ASSET-1",
        "lifecycleStatus",
        "Active",
        "lastUpdate",
        lastUpdate);
  }

  private static Map<String, Object> offering(String lastUpdate, String lifecycleStatus) {
    var term =
        Map.<String, Object>of(
            "name",
            TmfCatalogEventAuditor.TERM_NAME_CONTRACT_DEFINITION,
            TmfCatalogEventAuditor.CONTRACT_POLICY_KEY,
            Map.of(TmfCatalogEventAuditor.UID_KEY, "P-CONTRACT"),
            TmfCatalogEventAuditor.ACCESS_POLICY_KEY,
            Map.of(TmfCatalogEventAuditor.UID_KEY, "P-ACCESS"));
    return Map.<String, Object>of(
        "id",
        OFFERING_ID,
        "externalId",
        "CD-1",
        "lifecycleStatus",
        lifecycleStatus,
        "lastUpdate",
        lastUpdate,
        "productSpecification",
        Map.of("id", SPEC_ID),
        "productOfferingTerm",
        List.of(term));
  }

  private static Map<String, Object> notification(
      String eventType, String payloadName, Map<String, Object> entity) {
    return notification("evt-1", eventType, payloadName, entity);
  }

  private static Map<String, Object> notification(
      String eventiId, String eventType, String payloadName, Map<String, Object> entity) {
    return Map.<String, Object>of(
        "eventId",
        eventiId,
        "eventType",
        eventType,
        "eventTime",
        EVENT_TIME,
        "event",
        Map.of(payloadName, entity));
  }

  private LogRecordData single() {
    var records = exporter.getFinishedLogRecordItems();
    assertEquals(1, records.size());
    return records.get(0);
  }

  private static String attr(LogRecordData record, String key) {
    return record.getAttributes().get(AttributeKey.stringKey(key));
  }
}
