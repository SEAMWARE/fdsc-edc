package org.seamware.edc.services;

import org.eclipse.edc.spi.iam.AudienceResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;

public class NoopAudienceResolver implements AudienceResolver {

    private final Monitor monitor;

    public NoopAudienceResolver(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public Result<String> resolve(RemoteMessage remoteMessage) {
        monitor.debug("Tried to resolve audience.");
        return Result.success("Resolved");
    }
}
