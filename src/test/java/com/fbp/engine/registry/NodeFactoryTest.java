package com.fbp.engine.registry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fbp.engine.core.Node;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.node.mqtt.MqttSubscriberNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeFactoryTest {

    @Test
    @DisplayName("1. 정상 생성 확인")
    void testNormalCreation() {
        NodeRegistry registry = new NodeRegistry();
        
        // 팩토리 등록 (람다 활용)
        registry.register("MqttSub", (id, config) -> new MqttSubscriberNode(id, config));

        Map<String, Object> config = Map.of(
            "brokerUrl", "tcp://localhost:1883",
            "topic", "test/topic"
        );

        Node node = registry.create("MqttSub", "node-1", config);
        
        assertNotNull(node);
        assertEquals("node-1", node.getId());
    }

    @Test
    @DisplayName("2. 잘못된 config (필수 설정 누락) 시 예외 발생")
    void testInvalidConfig() {
        NodeRegistry registry = new NodeRegistry();
        registry.register("MqttSub", (id, config) -> new MqttSubscriberNode(id, config));

        Map<String, Object> invalidConfig = Map.of("topic", "test/topic");

        assertThrows(RuntimeException.class, () -> {
            registry.create("MqttSub", "node-2", invalidConfig);
        });
    }

    @Test
    @DisplayName("3. 람다 기반 팩토리 등록 확인")
    void testLambdaRegistration() {
        NodeRegistry registry = new NodeRegistry();

        assertDoesNotThrow(() -> {
            registry.register("SimpleNode", (id, config) -> new AbstractNode(id) {
                @Override protected void onProcess(Message message) {}
            });
        });
    }
}