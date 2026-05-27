package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;

//TODO 3-9
@Getter
public class PrintNode extends AbstractNode {
    private boolean silent = false;

    public PrintNode(String id) {
        this(id, Map.of());
    }

    public PrintNode(String id, Map<String, Object> config) {
        super(id, config);
        this.silent = (boolean) config.getOrDefault("silent", false);
        addInputPort("in");
    }

    @Override
    protected void onConfigUpdate(Map<String, Object> newConfig) {
        this.silent = (boolean) newConfig.getOrDefault("silent", false);
    }

    @Override
    protected void onProcess(Message message) {
        if (!silent) {
            System.out.println("[print-" + getId() +"] " + message);
        }
    }
}
