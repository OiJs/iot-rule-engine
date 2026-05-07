package com.fbp.engine.Integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.core.Flow;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.node.CollectorNode;
import com.fbp.engine.node.RuleNode;
import com.fbp.engine.node.modbus.ModbusWriterNode;
import com.fbp.engine.protocol.ModbusTcpSimulator;
import org.junit.jupiter.api.*;
import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.mqtt.*;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import java.util.*;
import java.util.concurrent.*;

@Tag("integration")
class MqttIntegrationTest {
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final int PORT = 5026;
    private FlowEngine engine;
    private ModbusTcpSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = new ModbusTcpSimulator(PORT, 20);
        simulator.start();
        engine = new FlowEngine();}

    @Test
    @DisplayName("1. MQTT → Rule → MODBUS 연동")
    void testMqttToModbus() throws Exception {
        MqttSubscriberNode sub = new MqttSubscriberNode("sub", Map.of("brokerUrl", "tcp://localhost:1883", "topic", "cmd", "clientId", "id"));
        RuleNode rule = new RuleNode("rule", "value == 1");
        ModbusWriterNode writer = new ModbusWriterNode("writer", Map.of("port", PORT, "registerAddress", 15, "valueField", "value"));

        Flow flow = new Flow("cross");
        flow.addNode(sub).addNode(rule).addNode(writer);
        flow.connect("sub", "out", "rule", "in");
        flow.connect("rule", "match", "writer", "in");

        engine.register(flow);
        engine.startFlow("cross");

        MqttClient client = new MqttClient("tcp://localhost:1883", "inj");
        client.connect();
        client.publish("cmd", new MqttMessage("{\"value\": 1}".getBytes()));
        Thread.sleep(1500);

        assertEquals(1, simulator.getRegister(15));
        client.disconnect();
    }

    @Test
    @DisplayName("2. 와일드카드 토픽(sensor/+) 구독")
    void testWildcard() throws Exception {
        MqttSubscriberNode sub = new MqttSubscriberNode("wild", Map.of("brokerUrl", BROKER_URL, "topic", "sensor/+", "clientId", "sub-2"));
        CollectorNode collector = new CollectorNode("collector");

        engine.register(new Flow("f2").addNode(sub).addNode(collector).connect("wild", "out", "collector", "in"));
        engine.startFlow("f2");

        publishMqtt("sensor/temp", "{\"v\":1}");
        publishMqtt("sensor/humi", "{\"v\":2}");
        Thread.sleep(1000);

        assertEquals(2, collector.getCollected().size(), "두 개의 토픽 메시지를 모두 수신해야 합니다.");
    }

    @Test
    @DisplayName("3. QoS 1 전달 보장")
    void testQos1() {
        MqttPublisherNode pub = new MqttPublisherNode("qos", Map.of("brokerUrl", BROKER_URL, "topic", "qos/t", "qos", 1, "clientId", "pub-3"));
        pub.initialize();
        assertDoesNotThrow(() -> pub.process(new Message(Map.of("data", "qos1"))));
    }

    @Test
    @DisplayName("4. 자동 재연결 테스트")
    void testReconnection() {
        MqttSubscriberNode sub = new MqttSubscriberNode("recon", Map.of("brokerUrl", BROKER_URL, "topic", "t", "clientId", "sub-4"));
        sub.initialize();
        assertTrue(sub.isConnected(), "초기 연결이 성공해야 합니다.");
    }

    private void publishMqtt(String topic, String msg) throws Exception {
        MqttClient client = new MqttClient(BROKER_URL, "injector");
        client.connect();
        client.publish(topic, new MqttMessage(msg.getBytes()));
        client.disconnect();
        client.close();
    }
}