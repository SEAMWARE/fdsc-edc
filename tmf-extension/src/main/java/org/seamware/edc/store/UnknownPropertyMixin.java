package org.seamware.edc.store;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.eclipse.edc.policy.model.Policy;

public abstract class UnknownPropertyMixin {

    @JsonAnySetter
    public abstract Policy.Builder extensibleProperty(String key, Object value);
}
