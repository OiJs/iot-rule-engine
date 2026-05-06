package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TimeWindowRuleNodeTest {
    private TimeWindowRuleNode node;
    private Connection alertConn;
    private Connection passConn;
    private final long WINDOW_MS = 1000;
    private final int THRESHOLD = 3;

    @BeforeEach
    void setUp() {
        node = new TimeWindowRuleNode("tw-node", 
            msg -> {
                Object temp = msg.getPayload().get("temperature");
                return (temp instanceof Number) && ((Number) temp).doubleValue() > 30.0;
            }, 
            WINDOW_MS, THRESHOLD);

        alertConn = new Connection("alert-link", 10);
        passConn = new Connection("pass-link", 10);

        node.getOutputPort("alert").connect(alertConn);
        node.getOutputPort("pass").connect(passConn);
    }

    @Test
    @DisplayName("1. 기준 미달 -> pass (횟수 부족)")
    void testBelowThreshold() {
        node.process(new Message(Map.of("temperature", 35.0)));
        node.process(new Message(Map.of("temperature", 35.0)));

        assertEquals(2, passConn.getBufferSize(), "횟수가 부족하면 pass 포트로 가야 합니다.");
        assertEquals(0, alertConn.getBufferSize());
    }

    @Test
    @DisplayName("2. 기준 도달 -> alert (횟수 충족)")
    void testReachThreshold() {
        node.process(new Message(Map.of("temperature", 35.0)));
        node.process(new Message(Map.of("temperature", 35.0)));
        node.process(new Message(Map.of("temperature", 35.0)));

        assertEquals(2, passConn.getBufferSize());
        assertEquals(1, alertConn.getBufferSize(), "3번째 메시지는 alert 포트로 가야 합니다.");
    }

    @Test
    @DisplayName("3. 시간 창 만료 (윈도우 밖 이벤트 제외)")
    void testWindowExpiration() throws InterruptedException {
        node.process(new Message(Map.of("temperature", 35.0)));
        node.process(new Message(Map.of("temperature", 35.0)));

        Thread.sleep(1100);

        node.process(new Message(Map.of("temperature", 35.0)));

        assertEquals(0, alertConn.getBufferSize(), "이전 이벤트가 만료되어 alert가 발생하면 안 됩니다.");
        assertEquals(3, passConn.getBufferSize());
    }

    @Test
    @DisplayName("4. 조건 불만족 메시지 (이벤트 미기록)")
    void testConditionMismatchNotCounted() {
        node.process(new Message(Map.of("temperature", 35.0)));
        node.process(new Message(Map.of("temperature", 35.0)));

        node.process(new Message(Map.of("temperature", 20.0)));

        assertEquals(3, passConn.getBufferSize());
        assertEquals(0, alertConn.getBufferSize(), "조건 불만족 메시지는 횟수에 포함되지 않아야 합니다.");
    }
}