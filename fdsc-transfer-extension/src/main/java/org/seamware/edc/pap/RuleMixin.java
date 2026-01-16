package org.seamware.edc.pap;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.edc.policy.model.*;

import java.util.List;

/**
 * Mixin to fix the broken odrl-json produced by the default implementation on serialization
 */
public abstract class RuleMixin {

    @JsonProperty("action")
    abstract Action getAction();

    @JsonProperty("constraint")
    abstract List<Constraint> getConstraints();

    @JsonProperty("duty")
    abstract List<Duty> getDuties();
}

