package com.fbp.engine.node;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CompositeRuleNodeTest {
    private LocalConnection matchConn;
    private LocalConnection mismatchConn;

    @BeforeEach
    void setUp() {
        matchConn = new LocalConnection("match-conn", 10);
        mismatchConn = new LocalConnection("mismatch-conn", 10);
    }

    @Test
    @DisplayName("1. AND — 모두 만족 (True AND True -> Match)")
    void testAndAllMatch() {
        CompositeRuleNode node = new CompositeRuleNode("and-node", CompositeRuleNode.Operator.AND);
        node.addCondition("temp", ">", 30.0);
        node.addCondition("humi", ">", 70.0);
        
        node.getOutputPort("match").connect(matchConn);

        node.process(new Message(Map.of("temp", 35.0, "humi", 80.0)));
        
        assertNotNull(matchConn.poll(), "모든 조건 만족 시 match 포트로 전송되어야 합니다.");
    }

    @Test
    @DisplayName("2. AND — 하나 불만족 (True AND False -> Mismatch)")
    void testAndOneMismatch() {
        CompositeRuleNode node = new CompositeRuleNode("and-node", CompositeRuleNode.Operator.AND);
        node.addCondition("temp", ">", 30.0);
        node.addCondition("humi", ">", 70.0);
        
        node.getOutputPort("mismatch").connect(mismatchConn);

        node.process(new Message(Map.of("temp", 35.0, "humi", 60.0)));
        
        assertNotNull(mismatchConn.poll(), "하나라도 불만족 시 mismatch 포트로 전송되어야 합니다.");
    }

    @Test
    @DisplayName("3. OR — 하나 만족 (True OR False -> Match)")
    void testOrOneMatch() {
        CompositeRuleNode node = new CompositeRuleNode("or-node", CompositeRuleNode.Operator.OR);
        node.addCondition("temp", ">", 30.0);
        node.addCondition("humi", ">", 70.0);
        
        node.getOutputPort("match").connect(matchConn);

        node.process(new Message(Map.of("temp", 35.0, "humi", 10.0)));
        
        assertNotNull(matchConn.poll(), "하나만 만족해도 OR 조건은 match 포트로 전송되어야 합니다.");
    }

    @Test
    @DisplayName("4. OR — 모두 불만족 (False OR False -> Mismatch)")
    void testOrAllMismatch() {
        CompositeRuleNode node = new CompositeRuleNode("or-node", CompositeRuleNode.Operator.OR);
        node.addCondition("temp", ">", 30.0);
        node.addCondition("humi", ">", 70.0);
        
        node.getOutputPort("mismatch").connect(mismatchConn);

        node.process(new Message(Map.of("temp", 20.0, "humi", 50.0)));
        
        assertNotNull(mismatchConn.poll(), "모든 조건 불만족 시 mismatch 포트로 전송되어야 합니다.");
    }

    @Test
    @DisplayName("5. 빈 조건 기본 동작 (AND는 Match, OR은 Mismatch)")
    void testEmptyConditions() {
        CompositeRuleNode emptyAnd = new CompositeRuleNode("empty-and", CompositeRuleNode.Operator.AND);
        emptyAnd.getOutputPort("match").connect(matchConn);
        emptyAnd.process(new Message(Map.of()));
        assertNotNull(matchConn.poll(), "조건이 없는 AND는 모든 메시지를 통과시켜야 합니다.");

        CompositeRuleNode emptyOr = new CompositeRuleNode("empty-or", CompositeRuleNode.Operator.OR);
        emptyOr.getOutputPort("mismatch").connect(mismatchConn);
        emptyOr.process(new Message(Map.of()));
        assertNotNull(mismatchConn.poll(), "조건이 없는 OR은 모든 메시지를 차단(mismatch)해야 합니다.");
    }
}