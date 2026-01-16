package org.seamware.edc;

import java.net.URI;

public final class SchemaBaseUriHolder {

    private static URI baseUri;

    public static void configure(URI uri) {
        baseUri = uri;
    }

    public static URI get() {
        return baseUri;
    }
}
