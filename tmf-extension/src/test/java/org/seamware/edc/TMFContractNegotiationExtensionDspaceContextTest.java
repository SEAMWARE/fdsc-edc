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
package org.seamware.edc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.Test;

public class TMFContractNegotiationExtensionDspaceContextTest {

  /** A policy constraint as stored in TMForum, using the DSP 2024/1 context. */
  private static final JsonObject DSPACE_2024_1_DOCUMENT =
      Json.createObjectBuilder()
          .add("@context", TMFContractNegotiationExtension.DSPACE_2024_1_CONTEXT)
          .add("@id", "urn:test:constraint")
          .add("dspace:credentialType", "MembershipCredential")
          .build();

  @Test
  public void testRegisterDspace2024Context_expands_documents_without_fetching_the_context() {
    // remote document resolution is disabled by default
    TitaniumJsonLd jsonLd = new TitaniumJsonLd(mock(Monitor.class));

    TMFContractNegotiationExtension.registerDspace2024Context(jsonLd);
    Result<JsonObject> expanded = jsonLd.expand(DSPACE_2024_1_DOCUMENT);

    assertTrue(expanded.succeeded(), "The document should expand with the local context.");
    assertTrue(
        expanded.getContent().containsKey("https://w3id.org/dspace/2024/1/credentialType"),
        "The dspace prefix should resolve to the DSP 2024/1 namespace.");
  }

  @Test
  public void testExpand_fails_without_the_registered_context() {
    TitaniumJsonLd jsonLd = new TitaniumJsonLd(mock(Monitor.class));

    assertTrue(
        jsonLd.expand(DSPACE_2024_1_DOCUMENT).failed(),
        "Without a local copy the context cannot be resolved.");
  }
}
