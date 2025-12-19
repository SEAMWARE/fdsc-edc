package org.seamware.edc.identity;

import org.eclipse.edc.spi.iam.AudienceResolver;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;

public class CounterPartyAddressAudienceResolver implements AudienceResolver {

    @Override
    public Result<String> resolve(RemoteMessage remoteMessage) {
        return Result.success(remoteMessage.getCounterPartyAddress());
    }
}
