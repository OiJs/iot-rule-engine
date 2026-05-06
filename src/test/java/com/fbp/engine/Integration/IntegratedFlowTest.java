package com.fbp.engine.Integration;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;

import com.fbp.engine.node.RuleNode;
import com.fbp.engine.node.modbus.ModbusWriterNode;
import com.fbp.engine.node.mqtt.MqttSubscriberNode;
import com.fbp.engine.node.mqtt.MqttPublisherNode;
import com.fbp.engine.protocol.ModbusTcpSimulator;
import java.io.IOException;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@DisplayName("MQTT ↔ MODBUS 통합 시나리오 테스트")
class IntegratedFlowTest {
    private static final int MODBUS_PORT = 5028;
    private static final String MQTT_BROKER = "tcp://localhost:1883";
    
    private ModbusTcpSimulator simulator;
    private FlowEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        simulator = new ModbusTcpSimulator(MODBUS_PORT, 10);
        simulator.start();
        Thread.sleep(500);

        engine = new FlowEngine();
    }

    @AfterEach
    void tearDown() throws IOException {
        simulator.stop();
    }

    private void publishMqtt(String topic, String payload) throws Exception {
        MqttClient testClient = new MqttClient(MQTT_BROKER, "test-sender");
        testClient.connect();
        testClient.publish(topic, new MqttMessage(payload.getBytes()));
        testClient.disconnect();
        testClient.close();
    }

    @Test
    @DisplayName("MQTT 수신부터 MODBUS 쓰기 및 알림 발행까지의 End-to-End 검증")
    void testFullIntegrationFlow() throws Exception {

        MqttSubscriberNode mqttSub = new MqttSubscriberNode("sub", Map.of(
                "brokerUrl", MQTT_BROKER, "topic", "sensors/temp", "clientId", "test-sub"));

        RuleNode rule = new RuleNode("rule", "temperature > 30.0");

        MqttPublisherNode mqttPub = new MqttPublisherNode("pub", Map.of(
                "brokerUrl", MQTT_BROKER, "topic", "alerts/overheat"));

        ModbusWriterNode modbusWriter = new ModbusWriterNode("writer", Map.of(
                "host", "localhost", "port", MODBUS_PORT, "registerAddress", 2, "valueField", "controlValue"));

        Flow flow = new Flow("integration-flow")
                .addNode(mqttSub).addNode(rule).addNode(mqttPub).addNode(modbusWriter)
                .connect("sub", "out", "rule", "in")
                .connect("rule", "match", "pub", "in")
                .connect("rule", "match", "writer", "in");


        engine.register(flow);
        engine.startFlow("integration-flow");
        Thread.sleep(1000);

        publishMqtt("sensors/temp", "{\"temperature\": 25.0, \"controlValue\": 1}");
        Thread.sleep(500);
        assertEquals(0, simulator.getRegister(2), "30도 이하일 때는 Modbus에 기록되지 않아야 함");

        Connection alertCapture = new Connection("capture", 10);

        publishMqtt("sensors/temp", "{\"temperature\": 35.5, \"controlValue\": 1}");
        Thread.sleep(1000);

        assertEquals(1, simulator.getRegister(2), "30도 초과 시 Modbus 레지스터에 1이 기록되어야 함");

        assertTrue(mqttSub.isConnected());
        assertTrue(modbusWriter.isConnected());
    }
}