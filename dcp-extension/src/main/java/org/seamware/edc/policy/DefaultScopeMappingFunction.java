package org.seamware.edc.policy;

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
        monitor.debug(String.format("Add scope %s for %s.", defaultScope, requestPolicyContext.requestContext().toString()));
        RequestScope.Builder requestScopeBuilder = requestPolicyContext.requestScopeBuilder();
        requestScopeBuilder.scope(defaultScope);
        return true;
    }
}
