package com.fbp.engine.flowtest;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.FilterNode;


import com.fbp.engine.node.mqtt.MqttPublisherNode;
import com.fbp.engine.node.mqtt.MqttSubscriberNode;
import java.util.Map;

//양방향 MQTT Flow
public class Task2_7_Flow {

    public static void main(String[] args) {
        FlowEngine engine = new FlowEngine();

        String brokerUrl = "tcp://localhost:1883";

        MqttSubscriberNode subscriber = new MqttSubscriberNode("sub", Map.of(
                "brokerUrl", brokerUrl,
                "clientId", "fbp-sub-final-" + System.currentTimeMillis(),
                "topic", "sensor/temp",
                "qos", 1
        ));

        FilterNode filter = new FilterNode("temp-filter", Map.of("key", "temperature", "threshold", 30.0));

        MqttPublisherNode publisher = new MqttPublisherNode("pub", Map.of(
                "brokerUrl", brokerUrl,
                "clientId", "fbp-pub-final-" + System.currentTimeMillis(),
                "topic", "alert/temp",
                "qos", 1
        ));

        Flow flow = new Flow("mqtt-bidirectional-flow")
                .addNode(subscriber)
                .addNode(filter)
                .addNode(publisher)
                .connect("sub", "out", "temp-filter", "in")
                .connect("temp-filter", "out", "pub", "in");

        engine.register(flow);
        engine.startFlow("mqtt-bidirectional-flow");

        System.out.println("mosquitto_sub -t \"alert/temp\" -v\n");
        System.out.println("mosquitto_pub -t \"alert/temp\" -m '{\"value\": 28.5}'\n");
    }
}
