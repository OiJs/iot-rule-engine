package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.Map;

public class FilterNode extends AbstractNode {
    private String key;
    private double threshold;

    public FilterNode(String id, Map<String, Object> config) {
        super(id, config);
        syncConfig(config);
        addInputPort("in");
        addOutputPort("out");
    }

    private void syncConfig(Map<String, Object> cfg) {
        this.key = (String) cfg.getOrDefault("key", "value");
        this.threshold = ((Number) cfg.getOrDefault("threshold", 0.0)).doubleValue();
    }

    @Override
    protected void onConfigUpdate(Map<String, Object> newConfig) {
        syncConfig(newConfig);
    }

    @Override
    protected void onProcess(Message message) {
        Number value = message.get(key);
        if (value != null && value.doubleValue() >= threshold) {
            send("out", message);
        }
    }
}