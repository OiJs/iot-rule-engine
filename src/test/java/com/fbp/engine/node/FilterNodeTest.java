package com.fbp.engine.node;

import static org.junit.jupiter.api.Assertions.*;
import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FilterNodeTest {
    private FilterNode filter;
    private Connection outConn;

    @BeforeEach
    void setUp() {
        filter = new FilterNode("f1", "temp", 30.0);
        outConn = new Connection("conn-out");
        filter.getOutputPort("out").connect(outConn);
    }

    @Test
    @DisplayName("조건 만족(35.0): 메시지가 출력 커넥션으로 전달되어야 함")
    void test1_PassCondition() {
        filter.process(new Message(Map.of("temp", 35.0)));
        assertEquals(1, outConn.getBufferSize());
    }

    @Test
    @DisplayName("조건 미달(25.0): 메시지가 차단되어야 함")
    void test2_FailCondition() {
        filter.process(new Message(Map.of("temp", 25.0)));
        assertEquals(0, outConn.getBufferSize());
    }

    @Test
    @DisplayName("경계값(30.0): 이상(>=) 조건이므로 전달되어야 함")
    void test3_BoundaryCondition() {
        filter.process(new Message(Map.of("temp", 30.0)));
        assertEquals(1, outConn.getBufferSize());
    }

    @Test
    @DisplayName("키 없음: 예외 없이 무시되어야 함")
    void test4_NoKeyInMessage() {
        assertDoesNotThrow(() -> filter.process(new Message(Map.of("humidity", 50.0))));
        assertEquals(0, outConn.getBufferSize());
    }

    @Test
    @DisplayName("Step5 #3: 포트 구성 확인 — in/out 포트가 등록되어야 함")
    void test5_PortsAreRegistered() {
        assertNotNull(filter.getInputPort("in"));
        assertNotNull(filter.getOutputPort("out"));
    }
}