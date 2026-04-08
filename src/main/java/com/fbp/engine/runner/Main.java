package com.fbp.engine.runner;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.AlertNode;
import com.fbp.engine.node.FileWriteNode;
import com.fbp.engine.node.LogNode;
import com.fbp.engine.node.TemperatureSensorNode;
import com.fbp.engine.node.ThresholdFilterNode;
import com.fbp.engine.node.TimerNode;

public class Main {
    public static void main(String[] args) {
        FlowEngine engine = new FlowEngine();

        TimerNode timer = new TimerNode("1sTimer", 1000);
        TemperatureSensorNode tempSensor = new TemperatureSensorNode("tempSensor", 15.0, 45.0);
        ThresholdFilterNode filter = new ThresholdFilterNode("filter", "temperature", 30.0);
        AlertNode alert = new AlertNode("alert");
        LogNode log = new LogNode("normal");
        FileWriteNode file = new FileWriteNode("file", "./log/temp-flow");

        Flow flow = new Flow("temperature-flow")
                .addNode(timer)
                .addNode(tempSensor)
                .addNode(filter)
                .addNode(alert)
                .addNode(log)
                .addNode(file)
                .connect("1sTimer", "out", "tempSensor", "trigger")
                .connect("tempSensor", "out", "filter", "in")
                .connect("filter", "alert", "alert", "in")
                .connect("filter", "normal", "normal", "in")
                .connect("normal", "out", "file", "in");

        engine.register(flow);

        engine.startFlow("temperature-flow");

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}

        engine.shutdown();
        System.out.println("시스템을 안전하게 종료했습니다.");
    }
}
