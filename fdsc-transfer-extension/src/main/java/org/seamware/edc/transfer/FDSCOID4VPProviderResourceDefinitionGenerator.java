package org.seamware.edc.transfer;

import org.eclipse.edc.connector.controlplane.transfer.spi.provision.ProviderResourceDefinitionGenerator;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.ResourceDefinition;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.jetbrains.annotations.Nullable;

import static org.seamware.edc.FDSCTransferControlExtension.TRANSFER_TYPE_HTTP_PULL;

public class FDSCProviderResourceDefinitionGenerator implements ProviderResourceDefinitionGenerator {

    private final Monitor monitor;

    public FDSCProviderResourceDefinitionGenerator(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public @Nullable ResourceDefinition generate(TransferProcess transferProcess, DataAddress assetAddress, Policy policy) {

        monitor.debug("Generate resource definition for " + transferProcess.getAssetId() + " - " + transferProcess.getCorrelationId());

        return FDSCOID4VPProviderResourceDefinition.Builder.newInstance()
                .assetId(transferProcess.getAssetId())
                .id(transferProcess.getCorrelationId())
                .transferProcessId(transferProcess.getId())
                .build();
    }

    @Override
    public boolean canGenerate(TransferProcess transferProcess, DataAddress assetAddress, Policy policy) {
        return transferProcess.getTransferType().equals(TRANSFER_TYPE_HTTP_PULL);
    }
}
