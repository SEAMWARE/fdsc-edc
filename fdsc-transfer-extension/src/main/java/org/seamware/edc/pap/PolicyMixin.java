package org.seamware.edc.pap;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;

import java.util.List;
import java.util.Map;

/**
 * Mixin to fix the broken odrl-json produced by the default implementation on serialization
 */
public abstract class PolicyMixin {

    @JsonAnyGetter
    abstract Map<String, Object> getExtensibleProperties();

    @JsonProperty("permission")
    abstract List<Permission> getPermissions();

    @JsonProperty("duty")
    abstract List<Duty> getDuties();

    @JsonProperty("target")
    abstract String getTarget();

    @JsonProperty("assigner")
    abstract String getAssigner();

    @JsonProperty("assignee")
    abstract String getAssignee();


}

