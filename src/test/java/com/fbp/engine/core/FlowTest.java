package com.fbp.engine.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.node.FilterNode;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowTest {
    private Flow flow;
    private TimerNode timer;
    private PrintNode printer;

    @BeforeEach
    void setUp() {
        flow = new Flow("test-flow");
        timer = new TimerNode("timer", Map.of("intervalMs", 500L));
        printer = new PrintNode("printer");
    }

    @Test
    @DisplayName("1. 노드 등록 확인")
    void test1_AddNode() {
        flow.addNode(timer);
        assertTrue(flow.getNodes().containsKey("timer"));
        assertEquals(timer, flow.getNodes().get("timer"));
    }

    @Test
    @DisplayName("2 & 3. 메서드 체이닝 및 정상 연결 확인")
    void test2_3_ChainingAndConnect() {
        flow.addNode(timer)
            .addNode(printer)
            .connect("timer", "out", "printer", "in");

        assertEquals(1, flow.getConnections().size());
        assertEquals("timer:out->printer:in", flow.getConnections().get(0).getId());
    }

    @Test
    @DisplayName("4. 존재하지 않는 소스 노드 ID 예외")
    void test4_InvalidSourceNode() {
        flow.addNode(printer);
        assertThrows(IllegalArgumentException.class, () -> 
            flow.connect("wrong-id", "out", "printer", "in")
        );
    }

    @Test
    @DisplayName("5. 존재하지 않는 대상 노드 ID 예외")
    void test5_InvalidTargetNode() {
        flow.addNode(timer);
        assertThrows(IllegalArgumentException.class, () -> 
            flow.connect("timer", "out", "wrong-id", "in")
        );
    }

    @Test
    @DisplayName("6. 존재하지 않는 소스 포트 예외")
    void test6_InvalidSourcePort() {
        flow.addNode(timer).addNode(printer);
        assertThrows(IllegalArgumentException.class, () -> 
            flow.connect("timer", "wrong-port", "printer", "in")
        );
    }

    @Test
    @DisplayName("7. 존재하지 않는 대상 포트 예외")
    void test7_InvalidTargetPort() {
        flow.addNode(timer).addNode(printer);
        assertThrows(IllegalArgumentException.class, () -> 
            flow.connect("timer", "out", "printer", "wrong-port")
        );
    }

    @Test
    @DisplayName("8. validate - 빈 Flow 검증")
    void test8_ValidateEmptyFlow() {
        List<String> errors = flow.validate();
        assertFalse(errors.isEmpty());
        assertTrue(errors.contains("노드가 1개 이상 있어야 합니다."));
    }

    @Test
    @DisplayName("9. validate - 정상 Flow 검증")
    void test9_ValidateNormalFlow() {
        flow.addNode(timer).addNode(printer).connect("timer", "out", "printer", "in");
        List<String> errors = flow.validate();
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("10 & 11. initialize 및 shutdown 호출 확인")
    void test10_11_Lifecycle() {
        flow.addNode(timer);
        assertDoesNotThrow(() -> {
            flow.initialize();
            flow.shutdown();
        });
    }

    @Test
    @DisplayName("12. 순환 참조 탐지 (도전 과제)")
    void test12_DetectCycle() {
        FilterNode nodeA = new FilterNode("nodeA", Map.of("key", "temp", "threshold", 30.0));
        FilterNode nodeB = new FilterNode("nodeB", Map.of("key", "temp", "threshold", 30.0));

        flow.addNode(nodeA)
                .addNode(nodeB)
                .connect("nodeA", "out", "nodeB", "in")
                .connect("nodeB", "out", "nodeA", "in");

        List<String> errors = flow.validate();

        assertTrue(errors.contains("순환 참조 감지"),
                "A->B->A 구조이므로 순환 참조 에러가 리스트에 포함되어야 합니다.");
    }
}