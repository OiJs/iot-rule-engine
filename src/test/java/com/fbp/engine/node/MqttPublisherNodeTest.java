package com.fbp.engine.node;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.mqtt.MqttPublisherNode;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class MqttPublisherNodeTest {

    public static class TestableMqttPublisherNode extends MqttPublisherNode {
        public TestableMqttPublisherNode(String id, Map<String, Object> config) {
            super(id, config);
        }

        @Override
        public void onProcess(Message message) {
            super.onProcess(message);
        }
    }

    private TestableMqttPublisherNode node;
    private final String brokerUrl = "tcp://localhost:1883";
    private final String defaultTopic = "test/pub";

    @BeforeEach
    void setUp() {
        Map<String, Object> config = Map.of(
                "brokerUrl", brokerUrl,
                "clientId", "test-pub-" + System.currentTimeMillis(),
                "topic", defaultTopic,
                "qos", 1
        );
        node = new TestableMqttPublisherNode("pub-node", config);
    }

    @Test
    @DisplayName("1. 포트 구성 확인")
    void testPortConfig() {
        assertNotNull(node.getInputPort("in"), "입력 포트 'in'이 존재해야 합니다.");
    }

    @Test
    @DisplayName("2. 초기 상태 확인")
    void testInitialState() {
        assertFalse(node.isConnected());
    }

    @Nested
    @Tag("integration")
    class IntegrationTests {

        @Test
        @DisplayName("4. Broker 연결 성공 확인")
        void testConnect() throws Exception {
            node.initialize();
            Thread.sleep(1000);
            assertTrue(node.isConnected());
            node.shutdown();
        }

        @Test
        @DisplayName("5. 메시지 발행 확인")
        void testPublish() throws Exception {
            node.initialize();
            Thread.sleep(1000);

            AtomicReference<String> received = new AtomicReference<>();
            MqttClient subClient = new MqttClient(brokerUrl, "sub-checker");

            try {
                subClient.setCallback(new MqttCallback() {
                    @Override
                    public void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) {
                        received.set(new String(message.getPayload()));
                    }
                    @Override public void disconnected(org.eclipse.paho.mqttv5.client.MqttDisconnectResponse r) {}
                    @Override public void mqttErrorOccurred(org.eclipse.paho.mqttv5.common.MqttException e) {}
                    @Override public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken t) {}
                    @Override public void connectComplete(boolean r, String s) {}
                    @Override public void authPacketArrived(int r, org.eclipse.paho.mqttv5.common.packet.MqttProperties p) {}
                });

                subClient.connect();
                subClient.subscribe(defaultTopic, 1);

                node.onProcess(new Message(Map.of("data", "hello-world")));

                long start = System.currentTimeMillis();
                while (received.get() == null && System.currentTimeMillis() - start < 5000) {
                    Thread.sleep(100);
                }

                assertNotNull(received.get(), "브로커로부터 메시지를 수신하지 못했습니다.");
                assertTrue(received.get().contains("hello-world"));
            } finally {
                if (subClient.isConnected()) subClient.disconnect();
                subClient.close();
            }
            node.shutdown();
        }

        @Test
        @DisplayName("6. 동적 토픽 발행 확인")
        void testDynamicTopic() throws Exception {
            node.initialize();
            Thread.sleep(1000);

            String dynamicTopic = (String) node.getConfig("topic");
            AtomicReference<String> caughtTopic = new AtomicReference<>();
            MqttClient subClient = new MqttClient(brokerUrl, "sub-dyn-checker");

            try {
                subClient.setCallback(new MqttCallback(){
                    @Override
                    public void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) {
                        caughtTopic.set(topic);
                    }
                    @Override public void disconnected(org.eclipse.paho.mqttv5.client.MqttDisconnectResponse r) {}
                    @Override public void mqttErrorOccurred(org.eclipse.paho.mqttv5.common.MqttException e) {}
                    @Override public void deliveryComplete(org.eclipse.paho.mqttv5.client.IMqttToken t) {}
                    @Override public void connectComplete(boolean r, String s) {}
                    @Override public void authPacketArrived(int r, org.eclipse.paho.mqttv5.common.packet.MqttProperties p) {}
                });

                subClient.connect();
                subClient.subscribe(dynamicTopic, 1); // 람다 제거

                node.onProcess(new Message(Map.of("topic", dynamicTopic, "msg", "ok")));

                long start = System.currentTimeMillis();
                while (caughtTopic.get() == null && System.currentTimeMillis() - start < 5000) {
                    Thread.sleep(100);
                }

                assertEquals(dynamicTopic, caughtTopic.get());
            } finally {
                if (subClient.isConnected()) subClient.disconnect();
                subClient.close();
            }
            node.shutdown();
        }

        @Test
        @DisplayName("7. shutdown 후 연결 해제 확인")
        void testShutdown() throws Exception {
            node.initialize();
            Thread.sleep(1000);
            node.shutdown();
            assertFalse(node.isConnected());
        }
    }
}