package org.seamware.edc;

import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.edr.EndpointDataReferenceService;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.seamware.edc.transfer.FDSCDataAddress;

import java.util.HashMap;
import java.util.logging.Logger;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.result.Result.success;

public class FDSCEndpointDataReferenceService implements EndpointDataReferenceService {

    public static final String ENDPOINT_TYPE = "https://w3id.org/idsa/v4.1/HTTP";

    public static final String PROPERTY_AGREEMENT_ID = "agreement_id";
    public static final String PROPERTY_ASSET_ID = "asset_id";
    public static final String PROPERTY_PROCESS_ID = "process_id";
    public static final String PROPERTY_FLOW_TYPE = "flow_type";
    private static final String PROPERTY_PARTICIPANT_ID = "participant_id";
    private final TransferConfig transferConfig;

    public FDSCEndpointDataReferenceService(TransferConfig transferConfig) {
        this.transferConfig = transferConfig;
    }


    @Override
    public Result<DataAddress> createEndpointDataReference(DataFlow dataFlow) {

        var fdscDataAddressBuilder =
                FDSCDataAddress.Builder.newInstance()
                        .clientId(dataFlow.getId())
                        .type(ENDPOINT_TYPE)
                        .property(EDC_NAMESPACE + "endpoint", "http://" + transferConfig.getTransferHost() + "/" + dataFlow.getId())
                        .property(EDC_NAMESPACE + "endpointType", ENDPOINT_TYPE);


        return Result.success(fdscDataAddressBuilder.build());
    }

    @Override
    public ServiceResult<Void> revokeEndpointDataReference(String s, String s1) {
        // TODO: check if it needs to be revoked
        return ServiceResult.success();
    }
}
