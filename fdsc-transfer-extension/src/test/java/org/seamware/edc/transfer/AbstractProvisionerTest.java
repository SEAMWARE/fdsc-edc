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

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.edc.policy.model.*;
import org.seamware.edc.apisix.Route;
import org.seamware.edc.domain.ExtendableProductSpecification;
import org.seamware.tmforum.productcatalog.model.CharacteristicValueSpecificationVO;
import org.seamware.tmforum.productcatalog.model.ProductSpecificationCharacteristicVO;

public class AbstractProvisionerTest {

  protected static final String TEST_ASSET_ID = "test-asset";
  protected static final String TEST_RESOURCE_DEFINITION_ID = "test-rdi";
  protected static final String TEST_TRANSFER_PROCESS_ID = "test-transfer";
  protected static final String TEST_ROUTE_ID = "test-route";
  protected static final String TEST_UPSTREAM = "http://my.upstream.svc.local";

  protected FDSCProvisionedResource getProvisionedResource(String transferProcessId) {
    return FDSCProvisionedResource.Builder.newInstance()
        .id("random-id")
        .resourceDefinitionId(TEST_ASSET_ID)
        .transferProcessId(transferProcessId)
        .build();
  }

  protected Route getRoute() {
    return new Route().setId(TEST_ROUTE_ID);
  }

  protected Route getRoute(String routeId) {
    return new Route().setId(routeId);
  }

  protected ExtendableProductSpecification getProductSpec(
      String assetId, List<ProductSpecificationCharacteristicVO> specChars) {
    ExtendableProductSpecification extendableProductSpecification =
        new ExtendableProductSpecification().setExternalId(assetId);
    extendableProductSpecification.setAtSchemaLocation(
        URI.create("http://base.uri/external-id.json"));

    extendableProductSpecification.setProductSpecCharacteristic(specChars);
    return extendableProductSpecification;
  }

  protected ProductSpecificationCharacteristicVO getUpstreamSpec(String upstreamAddress) {
    CharacteristicValueSpecificationVO characteristicValueSpecificationVO =
        new CharacteristicValueSpecificationVO().value(upstreamAddress).isDefault(true);

    return new ProductSpecificationCharacteristicVO()
        .productSpecCharacteristicValue(List.of(characteristicValueSpecificationVO))
        .id("upstreamAddress");
  }

  protected static Policy getTestPolicy() {
    return Policy.Builder.newInstance()
        .type(PolicyType.CONTRACT)
        .assigner("assigner")
        .assignee("assignee")
        .permission(getTestPermission())
        .build();
  }

  protected static Permission getTestPermission() {
    return Permission.Builder.newInstance()
        .action(getUse())
        .constraint(getTestConstraint())
        .build();
  }

  protected static Action getUse() {
    return Action.Builder.newInstance().type("use").build();
  }

  protected static Constraint getTestConstraint() {
    return AtomicConstraint.Builder.newInstance()
        .leftExpression(new LiteralExpression("odrl:dayOfWeek"))
        .operator(Operator.EQ)
        .rightExpression(new LiteralExpression(6))
        .build();
  }

  protected static Map<String, Object> getTestOdrlPolicy(String id) {
    return new LinkedHashMap<>(
        Map.of(
            "@type", "contract",
            "odrl:uid", id,
            "odrl:assigner", "assigner",
            "odrl:assignee", "assignee",
            "odrl:permission",
                Map.of(
                    "odrl:target",
                    TEST_ASSET_ID,
                    "odrl:action",
                    "odrl:use",
                    "odrl:constraint",
                    List.of(
                        Map.of(
                            "odrl:leftOperand",
                            "odrl:dayOfWeek",
                            "odrl:operator",
                            "odrl:eq",
                            "odrl:rightOperand",
                            6)))));
  }
}
