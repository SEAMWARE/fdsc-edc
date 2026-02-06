package org.seamware.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.iam.identitytrust.spi.scope.ScopeExtractorRegistry;
import org.eclipse.edc.iam.identitytrust.spi.verification.SignatureSuiteRegistry;
import org.eclipse.edc.iam.identitytrust.transform.to.JwtToVerifiableCredentialTransformer;
import org.eclipse.edc.iam.verifiablecredentials.spi.VcConstants;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestVersionPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.security.signature.jws2020.Jws2020SignatureSuite;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.seamware.edc.policy.CredentialScopeExtractorRegistry;
import org.seamware.edc.policy.DayOfWeekEvaluator;
import org.seamware.edc.policy.DefaultScopeMappingFunction;

import java.time.Clock;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

public class DCPExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;

    @Inject
    private Clock clock;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private TypeManager typeManager;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;

    @Inject
    private SignatureSuiteRegistry signatureSuiteRegistry;

    @Inject
    private TrustedIssuerRegistry trustedIssuerRegistry;

    @Inject
    private TypeTransformerRegistry typeTransformerRegistry;

    private ScopeExtractorRegistry scopeExtractorRegistry;

    @Override
    public void initialize(ServiceExtensionContext context) {

        DcpConfig dcpConfig = DcpConfig.fromConfig(context.getConfig());

        if (dcpConfig.isEnabled()) {
            // register signature suite
            var suite = new Jws2020SignatureSuite(typeManager.getMapper(JSON_LD));
            signatureSuiteRegistry.register(VcConstants.JWS_2020_SIGNATURE_SUITE, suite);
            DcpConfig.Scopes scopes = dcpConfig.getScopes();
            if (!scopes.catalog().isEmpty()) {
                monitor.debug("[DCPExtension] Configure post validator for catalog: " + scopes.catalog());
                policyEngine.registerPostValidator(RequestCatalogPolicyContext.class, new DefaultScopeMappingFunction(monitor, scopes.catalog())::apply);
            }
            if (!scopes.negotiation().isEmpty()) {
                monitor.debug("[DCPExtension] Configure post validator for negotiation: " + scopes.negotiation());
                policyEngine.registerPostValidator(RequestContractNegotiationPolicyContext.class, new DefaultScopeMappingFunction(monitor, scopes.negotiation())::apply);
            }
            if (!scopes.transfer().isEmpty()) {
                monitor.debug("[DCPExtension] Configure post validator for transfer: " + scopes.transfer());
                policyEngine.registerPostValidator(RequestTransferProcessPolicyContext.class, new DefaultScopeMappingFunction(monitor, scopes.transfer())::apply);
            }
            if (!scopes.version().isEmpty()) {
                monitor.debug("[DCPExtension] Configure post validator for version: " + scopes.version());
                policyEngine.registerPostValidator(RequestVersionPolicyContext.class, new DefaultScopeMappingFunction(monitor, scopes.version())::apply);
            }


            typeTransformerRegistry.register(new JsonValueToGenericTypeTransformer(typeManager, JSON_LD));
            typeTransformerRegistry.register(new FixedJwtToVerifiableCredentialTransformer(context.getMonitor()));

            // --- add policies here
            // TODO: support odrl-pap based evaluation in the future.

            // makes only sense to be evaluated on requests
            ruleBindingRegistry.bind("dayOfWeek", TransferProcessPolicyContext.TRANSFER_SCOPE);
            ruleBindingRegistry.bind("odrl:dayOfWeek", TransferProcessPolicyContext.TRANSFER_SCOPE);
            policyEngine.registerFunction(TransferProcessPolicyContext.class, Permission.class, "odrl:dayOfWeek", new DayOfWeekEvaluator<>(monitor, clock));
            policyEngine.registerFunction(TransferProcessPolicyContext.class, Permission.class, "dayOfWeek", new DayOfWeekEvaluator<>(monitor, clock));
        }
    }

    @Provider
    public ScopeExtractorRegistry scopeExtractorRegistry() {
        if (scopeExtractorRegistry == null) {
            this.scopeExtractorRegistry = new CredentialScopeExtractorRegistry(monitor);
        }
        return this.scopeExtractorRegistry;
    }

}
