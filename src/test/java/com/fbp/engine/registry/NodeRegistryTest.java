package com.fbp.engine.registry;

import com.fbp.engine.core.Node;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NodeRegistryTest {
    private NodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
    }

    @Test
    void test1_RegisterAndCreate() {
        registry.register("TestNode", (id, config) -> new MockNode(id, config));
        Node node = registry.create("TestNode", "node-1", Map.of());
        assertNotNull(node);
        assertEquals("node-1", node.getId());
    }

    @Test
    void test2_UnregisteredType() {
        assertThrows(NodeRegistryException.class, () -> registry.create("UnknownType", "node-1", Map.of()));
    }

    @Test
    void test3_DuplicateRegistration() {
        registry.register("TypeA", (id, config) -> new MockNode(id, config));
        assertThrows(NodeRegistryException.class, () -> registry.register("TypeA", (id, config) -> new MockNode(id, config)));
    }

    @Test
    void test4_GetRegisteredTypes() {
        registry.register("Type1", (id, config) -> new MockNode(id, config));
        registry.register("Type2", (id, config) -> new MockNode(id, config));
        Set<String> types = registry.getRegisteredTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains("Type1"));
        assertTrue(types.contains("Type2"));
    }

    @Test
    void test5_ConfigPassing() {
        Map<String, Object> config = Map.of("threshold", 30);
        registry.register("ConfigNode", (id, cfg) -> new MockNode(id, cfg));
        MockNode node = (MockNode) registry.create("ConfigNode", "node-1", config);
        assertEquals(30, node.getConfig().get("threshold"));
    }

    @Test
    void test6_IsRegistered() {
        registry.register("RegisteredType", (id, config) -> new MockNode(id, config));
        assertTrue(registry.isRegistered("RegisteredType"));
        assertFalse(registry.isRegistered("UnregisteredType"));
    }

    @Test
    void test7_NullOrEmptyType() {
        assertThrows(NodeRegistryException.class, () -> registry.register(null, (id, config) -> new MockNode(id, config)));
        assertThrows(NodeRegistryException.class, () -> registry.register("", (id, config) -> new MockNode(id, config)));
        assertThrows(NodeRegistryException.class, () -> registry.create(null, "id", Map.of()));
        assertThrows(NodeRegistryException.class, () -> registry.isRegistered(null));
    }

    private static class MockNode extends AbstractNode {
        private final Map<String, Object> config;
        MockNode(String id, Map<String, Object> config) {
            super(id);
            this.config = config;
        }
        public Map<String, Object> getConfig() { return config; }
        @Override protected void onProcess(Message message) {}
    }
}