package com.fbp.engine.flowtest;

import com.fbp.engine.cli.FbpCli;
import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;
import com.fbp.engine.parser.JsonFlowParser;
import com.fbp.engine.registry.NodeRegistry;
import java.util.Map;

public class TestCLI {
    public static void main(String[] args) {
        FlowEngine engine = new FlowEngine();
        NodeRegistry registry = new NodeRegistry();
        FlowManager flowManager = new FlowManager(engine, registry);
        JsonFlowParser parser = new JsonFlowParser();

        Flow flowA = new Flow("flowA")
                .addNode(new TimerNode("timerA", Map.of("intervalMs", 500L)))
                .addNode(new PrintNode("printA"))
                .connect("timerA", "out", "printA", "in");

        Flow flowB = new Flow("flowB")
                .addNode(new TimerNode("timerB", Map.of("intervalMs", 1000L)))
                .addNode(new PrintNode("printB"))
                .connect("timerB", "out", "printB", "in");

        engine.register(flowA);
        engine.register(flowB);

        FbpCli cli = new FbpCli(flowManager, parser);
        cli.start();
    }
}
