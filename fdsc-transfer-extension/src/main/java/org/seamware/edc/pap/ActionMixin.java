package org.seamware.edc.pap;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.Policy;

/**
 * Mixin to fix the broken odrl-json produced by the default implementation on serialization
 */
public abstract class ActionMixin {

    /**
     * building an action as
     * {
     *     "type": "http://www.w3.org/ns/odrl/2/use"
     * }
     * is syntactically and semantically wrong. In json-ld type has to be @type, and defining the concrete action should happen through @id
     */
    @JsonProperty("@id")
    abstract String getType();
}
