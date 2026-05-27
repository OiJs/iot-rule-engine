package com.fbp.engine.runner;

import com.fbp.engine.cli.FbpCli;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.ThresholdFilterNode;
import com.fbp.engine.node.TimerNode;
import com.fbp.engine.node.mqtt.MqttPublisherNode;
import com.fbp.engine.node.mqtt.MqttSubscriberNode;
import com.fbp.engine.parser.JsonFlowParser;
import com.fbp.engine.registry.NodeRegistry;

public class Main {
    public static void main(String[] args) {
        FlowEngine engine = new FlowEngine();
        NodeRegistry registry = new NodeRegistry();

        // Register nodes
        registry.register("MqttSubscriberNode", MqttSubscriberNode::new);
        registry.register("MqttPublisherNode", MqttPublisherNode::new);
        registry.register("TimerNode", TimerNode::new);
        registry.register("PrintNode", (id, config) -> new PrintNode(id));
        registry.register("ThresholdFilterNode", (id, config) -> {
            String fieldName = (String) config.getOrDefault("fieldName", "value");
            double threshold = ((Number) config.getOrDefault("threshold", 0.0)).doubleValue();
            return new ThresholdFilterNode(id, fieldName, threshold);
        });

        FlowManager flowManager = new FlowManager(engine, registry);
        
        JsonFlowParser parser = new JsonFlowParser();
        flowManager.addParser(parser);

        FbpCli cli = new FbpCli(flowManager, parser);
        cli.start();
    }
}

