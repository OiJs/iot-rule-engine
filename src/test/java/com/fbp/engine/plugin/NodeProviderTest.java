package com.fbp.engine.plugin;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeProviderTest {

    @Test
    @DisplayName("1. getNodeDescriptors: 구현체가 올바른 NodeDescriptor 목록을 반환해야 함")
    void test1_GetNodeDescriptors() {
        NodeProvider provider = new MockNodeProvider(List.of(
            new NodeDescriptor("TestNode", "Description", null, (id, config) -> null)
        ));

        List<NodeDescriptor> descriptors = provider.getNodeDescriptors();

        assertNotNull(descriptors);
        assertEquals(1, descriptors.size());
        assertEquals("TestNode", descriptors.get(0).typeName());
    }

    @Test
    @DisplayName("2. 빈 목록: 노드를 제공하지 않는 Provider는 빈 리스트를 반환해야 함")
    void test2_EmptyList() {
        NodeProvider provider = new MockNodeProvider(Collections.emptyList());

        List<NodeDescriptor> descriptors = provider.getNodeDescriptors();

        assertNotNull(descriptors);
        assertTrue(descriptors.isEmpty());
    }

    @Test
    @DisplayName("3. descriptor 정합성: 반환된 descriptor의 필수 값이 누락되지 않아야 함")
    void test3_DescriptorIntegrity() {
        NodeDescriptor descriptor = new NodeDescriptor(
            "ValidType", 
            "Valid Desc", 
            null, 
            (id, config) -> null
        );
        NodeProvider provider = new MockNodeProvider(List.of(descriptor));

        NodeDescriptor target = provider.getNodeDescriptors().get(0);

        assertAll("Descriptor 필드 검증",
            () -> assertNotNull(target.typeName(), "typeName은 null일 수 없습니다."),
            () -> assertFalse(target.typeName().isBlank(), "typeName은 비어있을 수 없습니다."),
            () -> assertNotNull(target.factory(), "factory는 null일 수 없습니다.")
        );
    }

    private static class MockNodeProvider implements NodeProvider {
        private final List<NodeDescriptor> descriptors;

        public MockNodeProvider(List<NodeDescriptor> descriptors) {
            this.descriptors = descriptors;
        }

        @Override
        public List<NodeDescriptor> getNodeDescriptors() {
            return descriptors;
        }
    }
}