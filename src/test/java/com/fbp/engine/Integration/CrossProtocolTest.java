package com.fbp.engine.Integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.RuleNode;
import com.fbp.engine.node.modbus.ModbusWriterNode;
import com.fbp.engine.node.mqtt.MqttSubscriberNode;
import com.fbp.engine.protocol.ModbusTcpSimulator;
import java.util.Map;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class CrossProtocolTest {
    private static final int PORT = 5026;
    private FlowEngine engine ;
    private ModbusTcpSimulator simulator;

    @BeforeEach
    void setUp() throws InterruptedException {
        this.engine = new FlowEngine();
        simulator = new ModbusTcpSimulator(PORT, 10);
        simulator.start();
        Thread.sleep(500);
    }

//    @Test
//    @DisplayName("1. MQTT → Rule → MODBUS 연동")
//    void testMqttToModbus() throws Exception {
//        MqttSubscriberNode sub = new MqttSubscriberNode("sub", Map.of("brokerUrl", "tcp://localhost:1883", "topic", "cmd"));
//        RuleNode rule = new RuleNode("rule", "value == 1");
//        ModbusWriterNode writer = new ModbusWriterNode("writer", Map.of("port", PORT, "registerAddress", 10, "valueField", "value"));
//
//        engine.register(new Flow("c").addNode(sub).addNode(rule).addNode(writer)
//                .connect("sub", "out", "rule", "in").connect("rule", "match", "writer", "in"));
//        engine.startFlow("c");
//
//        // MQTT 주입
//        publishMqtt("cmd", "{\"value\": 1}");
//        Thread.sleep(1000);
//
//        assertEquals(1, simulator.getRegister(10));
//    }

    @Test
    @DisplayName("3. 5분 안정성 테스트")
    void testStability() throws Exception {
        long end = System.currentTimeMillis() + (5 * 60 * 1000);
        while (System.currentTimeMillis() < end) {
            Thread.sleep(30000);
            System.out.println("장기 테스트 중...");
        }
    }


    private void publishMqtt(String topic, String msg) throws Exception {
        MqttClient client = new MqttClient("tcp://localhost:1880", "injector");
        client.connect();
        client.publish(topic, new MqttMessage(msg.getBytes()));
        client.disconnect();
        client.close();
    }
}