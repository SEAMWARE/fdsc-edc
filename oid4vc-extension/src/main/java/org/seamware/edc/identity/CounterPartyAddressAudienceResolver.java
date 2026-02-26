package org.seamware.edc.identity;

import org.eclipse.edc.spi.iam.AudienceResolver;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;

/**
 * Resolver for the counterparty from a remote message.
 * In the OID4VP case, the audience always is the counter-party.
 */
public class CounterPartyAddressAudienceResolver implements AudienceResolver {

    @Override
    public Result<String> resolve(RemoteMessage remoteMessage) {
        return Result.success(remoteMessage.getCounterPartyAddress());
    }
}
