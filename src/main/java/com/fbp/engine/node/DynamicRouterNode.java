package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.ArrayList;
import java.util.List;

public class DynamicRouterNode extends AbstractNode{
    private final List<RoutingRule> rules = new ArrayList<>();
    private String defaultPort = "default";

    public DynamicRouterNode(String id) {
        super(id);
        addOutputPort(defaultPort);
    }

    public DynamicRouterNode(String id, String defaultPort) {
        super(id);
        addOutputPort(defaultPort);
    }

    public void addRule(RoutingRule rule) {
        rules.add(rule);

        if(getOutputPort(rule.getTargetPort()) == null) {
            addOutputPort(rule.getTargetPort());
        }
    }

    public void setDefaultPort(String portName) {
        this.defaultPort = portName;
        if(getOutputPort(portName) == null) {
            addOutputPort(portName);
        }
    }

    @Override
    protected void onProcess(Message message) {
        for(RoutingRule rule : rules) {
            if(rule.matches(message)) {
                send(rule.getTargetPort(), message);
                return;
            }
        }

        send(defaultPort, message);
    }
}
