package org.seamware.edc.apisix;

import java.util.Map;

public class Upstream {

    private String type;
    private Map<String, Object> nodes;

    public String getType() {
        return type;
    }

    public Upstream setType(String type) {
        this.type = type;
        return this;
    }

    public Map<String, Object> getNodes() {
        return nodes;
    }

    public Upstream setNodes(Map<String, Object> nodes) {
        this.nodes = nodes;
        return this;
    }
}
