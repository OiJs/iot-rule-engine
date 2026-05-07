package com.fbp.engine.node;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuleNodeTest {
    private RuleNode node;
    private LocalConnection matchConn;
    private LocalConnection mismatchConn;

    @BeforeEach
    void setUp() {
        // "temperature > 30.0" 조건을 가진 RuleNode 생성
        node = new RuleNode("test-rule", "temperature > 30.0");
        matchConn = new LocalConnection("match-link", 10);
        mismatchConn = new LocalConnection("mismatch-link", 10);

        // 출력 포트에 테스트용 커넥션 연결
        node.getOutputPort("match").connect(matchConn);
        node.getOutputPort("mismatch").connect(mismatchConn);
    }

    @Test
    @DisplayName("1. 조건 만족 -> match 포트 전달 확인")
    void testConditionMatch() {
        Message msg = new Message(Map.of("temperature", 35.5));
        node.process(msg);

        assertNotNull(matchConn.poll(), "조건 만족 시 match 포트로 메시지가 가야 합니다.");
        assertEquals(0, mismatchConn.getQueueSize());
    }

    @Test
    @DisplayName("2. 조건 불만족 -> mismatch 포트 전달 확인")
    void testConditionMismatch() {
        Message msg = new Message(Map.of("temperature", 25.0));
        node.process(msg);

        assertNotNull(mismatchConn.poll(), "조건 불만족 시 mismatch 포트로 메시지가 가야 합니다.");
        assertEquals(0, matchConn.getQueueSize());
    }

    @Test
    @DisplayName("3. 포트 구성 확인 (in, match, mismatch)")
    void testPortConfiguration() {
        assertNotNull(node.getInputPort("in"));
        assertNotNull(node.getOutputPort("match"));
        assertNotNull(node.getOutputPort("mismatch"));
    }

    @Test
    @DisplayName("4. null 필드 처리 (필드가 없는 메시지)")
    void testNullFieldHandling() {

        Message msg = new Message(Map.of("humidity", 50.0));
        assertDoesNotThrow(() -> node.process(msg));
        assertNotNull(mismatchConn.poll());
    }

    @Test
    @DisplayName("5. 다수 메시지 분기 (혼합 처리)")
    void testMultipleMessages() {
        node.process(new Message(Map.of("temperature", 40.0))); // match
        node.process(new Message(Map.of("temperature", 10.0))); // mismatch
        node.process(new Message(Map.of("temperature", 31.0))); // match

        assertEquals(2, matchConn.getQueueSize());
        assertEquals(1, mismatchConn.getQueueSize());
    }
}