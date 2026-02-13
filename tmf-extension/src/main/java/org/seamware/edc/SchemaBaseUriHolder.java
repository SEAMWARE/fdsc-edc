package org.seamware.edc;

import java.net.URI;

/**
 * Static helper to allow configuration of the base address for the extension-schemas.
 */
public final class SchemaBaseUriHolder {

    private static URI baseUri;

    public static void configure(URI uri) {
        baseUri = uri;
    }

    public static URI get() {
        return baseUri;
    }
}
