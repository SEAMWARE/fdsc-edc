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
package org.seamware.edc.policy;

/*-
 * #%L
 * dcp-extension
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

import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.iam.RequestScope;
import org.eclipse.edc.spi.monitor.Monitor;

public class DefaultScopeMappingFunction implements PolicyValidatorRule<RequestPolicyContext> {

  private final Monitor monitor;
  private final String defaultScope;

  public DefaultScopeMappingFunction(Monitor monitor, String defaultScope) {
    this.monitor = monitor;
    this.defaultScope = defaultScope;
  }

  @Override
  public Boolean apply(Policy policy, RequestPolicyContext requestPolicyContext) {
    monitor.debug(
        String.format(
            "Add scope %s for %s.",
            defaultScope, requestPolicyContext.requestContext().toString()));
    RequestScope.Builder requestScopeBuilder = requestPolicyContext.requestScopeBuilder();
    requestScopeBuilder.scope(defaultScope);
    return true;
  }
}
