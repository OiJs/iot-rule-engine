package com.fbp.engine.node;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.collector.MessageCollector;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.mqtt.MqttSubscriberNode;
import java.util.List;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.*;

import java.util.Map;

class MqttSubscriberNodeTest {
    private MqttSubscriberNode node;
    private final String brokerUrl = "tcp://localhost:1883";
    private final String topic = "test/sub";

    @BeforeEach
    void setUp() {
        Map<String, Object> config = Map.of(
                "brokerUrl", brokerUrl,
                "clientId", "test-sub-" + System.currentTimeMillis(),
                "topic", topic,
                "qos", 1
        );
        node = new MqttSubscriberNode("sub-node", config);
    }

    @Test
    @DisplayName("1. 포트 구성 확인")
    void testPortConfig() {
        assertNotNull(node.getOutputPort("out"));
    }

    @Test
    @DisplayName("2. 초기 상태 확인")
    void testInitialState() {
        assertFalse(node.isConnected());
    }

    @Test
    @DisplayName("3. Config 조회 확인")
    void testConfig() {
        assertEquals(brokerUrl, node.getConfig("brokerUrl"));
    }

    @Nested
    @Tag("integration")
    class IntegrationTests {
        @Test
        @DisplayName("6. 연결 성공 확인")
        void testConnect() throws Exception {
            node.initialize();
            Thread.sleep(1000);
            assertTrue(node.isConnected());
            node.shutdown();
        }

        @Test
        @DisplayName("7-8. 메시지 수신 및 토픽 정보 포함")
        void testMessageReceive() throws Exception {
            MessageCollector collector = new MessageCollector("collector");

            FlowEngine engine = new FlowEngine();
            Flow flow = new Flow("test-flow")
                    .addNode(node)
                    .addNode(collector)
                    .connect("sub-node", "out", "collector", "in");

            engine.register(flow);
            engine.startFlow("test-flow");

            Thread.sleep(1500);

            MqttClient sender = new MqttClient(brokerUrl, "test-sender-" + System.currentTimeMillis());
            try {
                sender.connect();
                sender.publish(topic, new MqttMessage("{\"temp\":25.5}".getBytes()));
                System.out.println("[Test] 메시지 발행 완료");
            } finally {
                if (sender.isConnected()) sender.disconnect();
                sender.close();
            }

            long startTime = System.currentTimeMillis();
            while (collector.getMessages().isEmpty() && System.currentTimeMillis() - startTime < 5000) {
                Thread.sleep(200);
            }

            List<Message> receivedList = collector.getMessages();
            assertFalse(receivedList.isEmpty(), "데이터가 전달되지 않았습니다. 포트 연결 상태를 확인하세요.");
            assertEquals(topic, receivedList.get(0).getPayload().get("topic"));

            engine.stopFlow("test-flow");
        }
    }
}