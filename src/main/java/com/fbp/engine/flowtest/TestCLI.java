package com.fbp.engine.flowtest;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;

public class TestCLI {
    public static void main(String[] args) {
        FlowEngine engine = new FlowEngine();

        Flow flowA = new Flow("flowA")
                .addNode(new TimerNode("timerA", 500))
                .addNode(new PrintNode("printA"))
                .connect("timerA", "out", "printA", "in");

        Flow flowB = new Flow("flowB")
                .addNode(new TimerNode("timerB", 1000))
                .addNode(new PrintNode("printB"))
                .connect("timerB", "out", "printB", "in");

        engine.register(flowA);
        engine.register(flowB);

        engine.startEngineCLI();
    }
}