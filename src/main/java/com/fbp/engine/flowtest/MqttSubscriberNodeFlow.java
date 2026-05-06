package com.fbp.engine.flowtest;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.LogNode;

import com.fbp.engine.node.mqtt.MqttSubscriberNode;
import java.util.Map;

public class MqttSubscriberNodeFlow {
    public static void main(String[] args) {
        FlowEngine engine = new FlowEngine();

        Map<String, Object> mqttConfig = Map.of(
                "brokerUrl", "tcp://localhost:1883",
                "clientId", "fbp-engine-sub-001",
                "topic", "sensor/temp",
                "qos", 1
        );
        MqttSubscriberNode subscriber = new MqttSubscriberNode("mqtt-sub", mqttConfig);

        LogNode printer = new LogNode("printer");

        Flow flow = new Flow("mqtt-test-flow")
                .addNode(subscriber)
                .addNode(printer)
                .connect("mqtt-sub", "out", "printer", "in");

        engine.register(flow);
        engine.startFlow("mqtt-test-flow");

        System.out.println("\nFBP 엔진이 가동되었습니다. 'sensor/temp' 토픽을 구독 중입니다.");
        System.out.println("터미널에서 mosquitto_pub 명령어로 메시지 발행");

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("시스템을 종료합니다.");
            engine.shutdown();
        }
    }
}