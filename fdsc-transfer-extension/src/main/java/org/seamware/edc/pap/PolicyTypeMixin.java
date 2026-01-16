package org.seamware.edc.pap;

import com.fasterxml.jackson.annotation.JsonValue;

public abstract class PolicyTypeMixin {

    /**
     * The policy type is serialized to {"@policytype": "the-type"}, which is not a valid value for "@type" in json-ld.
     * Thus, we flatten it.
     */
    @JsonValue
    abstract String getType();

}
