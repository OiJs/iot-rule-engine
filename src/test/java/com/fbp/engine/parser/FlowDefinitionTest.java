package com.fbp.engine.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FlowDefinitionTest {

    @Test
    @DisplayName("1. 불변성: 생성 후 노드/연결 목록 수정 불가 확인")
    void test1_Immutability() {
        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(new NodeDefinition("n1", "type", Map.of()));
        
        FlowDefinition def = new FlowDefinition("f1", "name", "desc", nodes, List.of());

        assertThrows(UnsupportedOperationException.class, () -> {
            def.nodes().add(new NodeDefinition("n2", "type", Map.of()));
        });
    }

    @Test
    @DisplayName("2. 노드 조회: getNode(id)로 특정 노드 정의 조회")
    void test2_GetNode() {
        NodeDefinition n1 = new NodeDefinition("node-1", "MqttSubscriber", Map.of());
        FlowDefinition def = new FlowDefinition("f1", "name", "desc", List.of(n1), List.of());

        NodeDefinition found = def.getNode("node-1");
        
        assertNotNull(found);
        assertEquals("MqttSubscriber", found.type());
        assertNull(def.getNode("non-existent"));
    }

    @Test
    @DisplayName("3. 연결 유효성: 모든 연결이 존재하는 노드를 참조하는지 검증")
    void test3_ConnectionValidity() {
        NodeDefinition n1 = new NodeDefinition("n1", "type", Map.of());
        ConnectionDefinition c1 = new ConnectionDefinition("n1:out", "n2:in"); // n2는 존재하지 않음

        FlowDefinition invalidDef = new FlowDefinition("f1", "name", "desc", List.of(n1), List.of(c1));

        assertThrows(IllegalArgumentException.class, invalidDef::validate);
    }
}