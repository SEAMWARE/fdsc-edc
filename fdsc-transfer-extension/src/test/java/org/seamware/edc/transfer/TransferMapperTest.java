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
package org.seamware.edc.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.seamware.edc.TransferConfig;
import org.seamware.edc.apisix.OpenidConnectPlugin;

class TransferMapperTest {

  private static final String OID_HOST = "http://controlplane:8081/api";
  private static final String DEFAULT_DISCOVERY = OID_HOST + "/.well-known/openid-configuration";
  private static final String OPENID_CONNECT_PLUGIN = "openid-connect";

  private final TransferMapper transferMapper =
      new TransferMapper(
          TransferConfig.Builder.newInstance()
              .transferHost("transfer.host")
              .apisix(new TransferConfig.Apisix("http://apisix-admin:9180", "token", null))
              .dcp(
                  new TransferConfig.Dcp.Builder()
                      .enabled(true)
                      .oidConfigBuilder(new TransferConfig.OidConfig.Builder().host(OID_HOST))
                      .build())
              .build(),
          "default-context");

  @ParameterizedTest(name = "context {0}")
  @CsvSource(
      value = {
        "context-b, /Y29udGV4dC1i",
        "did:web:a.example%3A8443, /ZGlkOndlYjphLmV4YW1wbGUlM0E4NDQz",
        "ctx/with space, /Y3R4L3dpdGggc3BhY2U",
        "default-context, ''",
        "null, ''",
      },
      nullValues = "null")
  void toDcpServiceRoute_discoveryPointsAtTransferContext(
      String participantContextId, String expectedSuffix) {
    OpenidConnectPlugin plugin =
        (OpenidConnectPlugin)
            transferMapper
                .toDcpServiceRoute("transfer-1", "upstream:8080", participantContextId)
                .getPlugins()
                .get(OPENID_CONNECT_PLUGIN);

    assertEquals(DEFAULT_DISCOVERY + expectedSuffix, plugin.getDiscovery());
  }
}
