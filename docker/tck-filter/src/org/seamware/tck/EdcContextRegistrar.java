package org.seamware.tck;

import org.eclipse.dataspacetck.core.api.message.MessageSerializer;

import java.net.URI;

/**
 * Registers EDC-specific JSON-LD context documents with the TCK's MessageSerializer
 * so the TCK can process JSON-LD responses from the EDC without fetching remote contexts.
 *
 * <p>EDC 0.14.1 includes {@code https://w3id.org/edc/dspace/v0.0.1} in its DSP 2025-1 responses,
 * which the TCK doesn't have cached by default.
 */
public class EdcContextRegistrar {

    /** The EDC-specific DSP context URL that EDC 0.14.1 includes in responses. */
    private static final String EDC_DSPACE_CONTEXT_URL = "https://w3id.org/edc/dspace/v0.0.1";

    /** Resource path within this JAR for the cached context document. */
    private static final String EDC_DSPACE_CONTEXT_RESOURCE = "dspace-edc-context-v1.jsonld";

    private EdcContextRegistrar() {
    }

    /** Registers the EDC context with the TCK's MessageSerializer. */
    public static void register() {
        MessageSerializer.registerDocument(
                URI.create(EDC_DSPACE_CONTEXT_URL),
                EDC_DSPACE_CONTEXT_RESOURCE);
    }
}
