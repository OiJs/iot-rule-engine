package com.fbp.engine.flowtest;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.LogNode;
import com.fbp.engine.node.RuleNode;
import com.fbp.engine.node.modbus.ModbusWriterNode;
import com.fbp.engine.node.mqtt.MqttPublisherNode;
import com.fbp.engine.node.mqtt.MqttSubscriberNode;
import com.fbp.engine.protocol.ModbusTcpSimulator;
import java.util.Map;

//TODO Stage2 4-4
public class IntegratedControlFlow {
    public static void main(String[] args) {
        FlowEngine engine = new FlowEngine();

        int port = 5020;

        ModbusTcpSimulator simulator = new ModbusTcpSimulator(port, 10);
        simulator.start();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        MqttSubscriberNode mqttSub = new MqttSubscriberNode("mqtt-sub", Map.of(
                "brokerUrl", "tcp://localhost:1883",
                "topic", "alert/overheat",
                "clientId", "temp-sensor-sub"
        ));

        RuleNode ruleNode = new RuleNode("temp-rule", "temperature > 30.0");

        MqttPublisherNode mqttPub = new MqttPublisherNode("mqtt-pub", Map.of(
                "brokerUrl", "tcp://localhost:1883",
                "topic", "alert/overheat"
        ));

        ModbusWriterNode modbusWriter = new ModbusWriterNode("modbus-writer", Map.of(
                "host", "localhost",
                "port", port,
                "registerAddress", 2,
                "valueField", "controlValue",
                "scale", 1.0
        ));

        LogNode logNode = new LogNode("log-node");

        Flow integratedFlow = new Flow("integrated-flow")
                .addNode(mqttSub)
                .addNode(mqttPub)
                .addNode(ruleNode)
                .addNode(modbusWriter)
                .addNode(logNode)
                .connect("mqtt-sub", "out", "temp-rule", "in")
                .connect("temp-rule", "match", "modbus-writer", "in")
                .connect("temp-rule", "mismatch", "log-node", "in");

        engine.register(integratedFlow);
        engine.startFlow("integrated-flow");
        System.out.println("통합 플로우 실행 중...");
    }
}
