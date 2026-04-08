package com.fbp.engine.node;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.message.Message;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlertNodeTest {

    private AlertNode alertNode;

    @BeforeEach
    void setUp() {
        alertNode = new AlertNode("alert");
    }

    @Test
    @DisplayName("정상 처리: 필수 정보 포함")
    void test1_NormalAlert() {
        Message msg;
        msg = new Message(Map.of(
            "sensorId", "temp-01",
            "temperature", 35.5,
            "unit", "°C"
        ));

        assertDoesNotThrow(() -> {
            alertNode.onProcess(msg);
        });
    }

    @Test
    @DisplayName("빈 메시지 수신 시 대응")
    void test4_EmptyMessage() {
        Message emptyMsg = new Message(Map.of());

        assertDoesNotThrow(() -> {
            alertNode.onProcess(emptyMsg);
        });
    }
}
