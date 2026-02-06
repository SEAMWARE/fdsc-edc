package org.seamware.edc.tir;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.seamware.til.model.CredentialsVO;
import org.seamware.til.model.TrustedIssuerVO;

import java.util.Set;
import java.util.stream.Collectors;

public class EbsiTrustedIssuersRegistry implements TrustedIssuerRegistry {

    private final Monitor monitor;
    private final TirClient tirClient;

    public EbsiTrustedIssuersRegistry(Monitor monitor, TirClient tirClient) {
        this.monitor = monitor;
        this.tirClient = tirClient;
    }

    @Override
    public void register(Issuer issuer, String credentialType) {
        tirClient.getIssuer(issuer.id())
                .ifPresentOrElse(ti -> {
                            ti.addCredentialsItem(new CredentialsVO()
                                    .credentialsType(credentialType));
                            tirClient.putIssuer(ti);
                        }, () ->
                                tirClient.createIssuer(new TrustedIssuerVO().did(issuer.id())
                                        .addCredentialsItem(new CredentialsVO()
                                                .credentialsType(credentialType)))
                );
    }

    @Override
    public Set<String> getSupportedTypes(Issuer issuer) {
        return tirClient.getIssuer(issuer.id())
                .map(trustedIssuer ->
                        trustedIssuer.getCredentials()
                                .stream()
                                .map(CredentialsVO::getCredentialsType)
                                .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

}
