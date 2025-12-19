package org.seamware.edc.transfer;

import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.util.function.Function;

public class FDSCEndpointGenerator implements Function<DataAddress, Endpoint> {

    @Override
    public Endpoint apply(DataAddress dataAddress) {
       return Endpoint.url(dataAddress.getStringProperty("endpointUrl"));
    }
}
