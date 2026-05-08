//package com.fbp.engine.flowtest;
//
//import com.fbp.engine.core.Flow;
//import com.fbp.engine.core.FlowEngine;
//import com.fbp.engine.node.TemperatureSensorNode;
//import com.fbp.engine.node.TimerNode;
//
//import com.fbp.engine.node.mqtt.MqttPublisherNode;
//import java.util.Map;
//
//public class MqttPublisherNodeFlow {
//
//    public static void main(String[] args) {
//        FlowEngine engine = new FlowEngine();
//
//        String brokerUrl = "tcp://localhost:1883";
//
//        TimerNode timer = new TimerNode("timer", 3000);
//
//        TemperatureSensorNode sensor = new TemperatureSensorNode("sensor", 20.0, 30.0);
//
//        MqttPublisherNode publisher = new MqttPublisherNode("pub", Map.of(
//                "brokerUrl", brokerUrl,
//                "clientId", "fbp-pub-test-" + System.currentTimeMillis(),
//                "topic", "alert/temp",
//                "qos", 1
//        ));
//
//        Flow flow = new Flow("pub-test-flow")
//                .addNode(timer)
//                .addNode(sensor)
//                .addNode(publisher)
//                .connect("timer", "out", "sensor", "trigger")
//                .connect("sensor", "out", "pub", "in");
//
//        engine.register(flow);
//
//        engine.startFlow("pub-test-flow");
//    }
//}