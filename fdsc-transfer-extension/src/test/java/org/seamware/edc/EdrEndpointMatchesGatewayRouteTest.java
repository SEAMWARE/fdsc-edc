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

/*-
 * #%L
 * fdsc-transfer-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.Test;
import org.seamware.edc.apisix.Route;
import org.seamware.edc.transfer.FDSCDcpProviderResourceDefinition;
import org.seamware.edc.transfer.FDSCOID4VPProviderResourceDefinition;
import org.seamware.edc.transfer.TransferMapper;

/**
 * The EDR endpoint and the gateway route are built by two different classes and have to agree. They
 * did not, and the way it surfaced is the reason this test exists rather than a comment.
 *
 * <p>{@link FDSCEndpoints#buildEndpoint} used to return {@code protocol://host/{dataFlowId}} when
 * the data flow carried no {@code transferPath}, while {@link TransferMapper} registers the route
 * for that transfer as {@code /{transferProcessId}/*}. APISIX's radixtree does not match that
 * pattern against a URL ending at the id, so a consumer following the EDR verbatim fell through to
 * another route on the same host, whose JWKS does not hold the key this EDR's token is signed with,
 * and was told {@code 401 invalid_token, error_description="RSA key with id sig not found"} - an
 * accusation against a token that was never the problem. Measured on the demo dataspace: the same
 * token on the same endpoint answered 401 bare and 404 - authenticated, upstream empty at the root
 * - with one slash appended.
 *
 * <p>So this asserts the contract directly: whatever either side does, the path of the endpoint
 * handed to a consumer must be matched by the route registered for it.
 */
public class EdrEndpointMatchesGatewayRouteTest {

  private static final String ID = "5a7d2c71-1775-43e7-91d7-74d6d8a6ea41";
  private static final String UPSTREAM = "data-service-scorpio:9090";
  private static final String HOST = "mp-data-service.seamware.io";

  private final TransferConfig transferConfig =
      TransferConfig.Builder.newInstance()
          .enabled(true)
          .transferHost(HOST)
          .transferProtocol("https")
          .apisix(new TransferConfig.Apisix("http://apisix-admin:9180", "token", null))
          .dcp(
              new TransferConfig.Dcp.Builder()
                  .enabled(true)
                  .oidConfigBuilder(
                      new TransferConfig.OidConfig.Builder().host("http://controlplane:8081/api"))
                  .build())
          .oid4Vc(
              new TransferConfig.Oid4Vc.Builder()
                  .enabled(true)
                  .verifierHost("https://verifier.example")
                  .verifierInternalHost("verifier:3000")
                  .opaHost("http://localhost:8181")
                  .odrlPapHost("http://odrl-pap:8080")
                  .credentialsConfigAddress("http://verifier:8090")
                  .build())
          .build();

  private final TransferMapper transferMapper = new TransferMapper(transferConfig);

  @Test
  public void dcpRouteMatchesTheEndpointWithNoTransferPath() {
    assertRouteMatches(dcpRoute(), FDSCEndpoints.buildEndpoint(transferConfig, dataFlow(null)));
  }

  @Test
  public void dcpRouteMatchesTheEndpointWithATransferPath() {
    assertRouteMatches(
        dcpRoute(),
        FDSCEndpoints.buildEndpoint(
            transferConfig, dataFlow("/ngsi-ld/v1/entities?type=CrowdFlowObserved")));
  }

  @Test
  public void oid4vpRouteMatchesTheEndpointWithNoTransferPath() {
    assertRouteMatches(oid4vpRoute(), FDSCEndpoints.buildEndpoint(transferConfig, dataFlow(null)));
  }

  @Test
  public void oid4vpRouteMatchesTheEndpointWithATransferPath() {
    assertRouteMatches(
        oid4vpRoute(),
        FDSCEndpoints.buildEndpoint(transferConfig, dataFlow("/ngsi-ld/v1/entities")));
  }

  /**
   * APISIX's `/prefix/*` matches a path that starts with `/prefix/`, and NOT one that stops at
   * `/prefix`. That one character is the whole bug, so the rule is spelled out here rather than
   * assumed.
   */
  private static void assertRouteMatches(Route route, String endpoint) {
    String uri = route.getUri();
    assertTrue(uri.endsWith("/*"), "the route is expected to be a prefix route, was " + uri);
    String prefix = uri.substring(0, uri.length() - 1);
    String path = URI.create(endpoint).getPath();
    assertTrue(
        path.startsWith(prefix),
        "the gateway route "
            + uri
            + " does not match the path of the EDR endpoint "
            + endpoint
            + " (path "
            + path
            + "); a consumer using this EDR as handed over is refused by the gateway");
  }

  private Route dcpRoute() {
    return transferMapper.toDcpServiceRoute(
        FDSCDcpProviderResourceDefinition.Builder.newInstance()
            .id("resource")
            .transferProcessId(ID)
            .assetId("asset")
            .build(),
        UPSTREAM);
  }

  private Route oid4vpRoute() {
    return transferMapper.toOid4VpServiceRoute(
        FDSCOID4VPProviderResourceDefinition.Builder.newInstance()
            .id("resource")
            .transferProcessId(ID)
            .assetId("asset")
            .build(),
        UPSTREAM,
        "http://odrl-pap:8080/policy");
  }

  private static DataFlow dataFlow(String transferPath) {
    DataAddress.Builder source = DataAddress.Builder.newInstance().type("FDSC");
    if (transferPath != null) {
      source.property("transferPath", transferPath);
    }
    return DataFlow.Builder.newInstance().id(ID).source(source.build()).build();
  }
}
