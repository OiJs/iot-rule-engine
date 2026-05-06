package com.fbp.engine.rule;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuleExpressionTest {

    @Test
    @DisplayName("1. 파싱 - 숫자 비교 (temperature > 30.0)")
    void testNumericParsing() {
        RuleExpression expr = RuleExpression.parse("temperature > 30.0");
        assertTrue(expr.evaluate(new Message(Map.of("temperature", 30.1))));
        assertFalse(expr.evaluate(new Message(Map.of("temperature", 29.9))));
    }

    @Test
    @DisplayName("2. 파싱 - 문자열 비교 (status == ON)")
    void testStringParsing() {
        RuleExpression expr = RuleExpression.parse("status == ON");
        assertTrue(expr.evaluate(new Message(Map.of("status", "ON"))));
        assertFalse(expr.evaluate(new Message(Map.of("status", "OFF"))));
    }

    @Test
    @DisplayName("3. 모든 연산자 검증 (>, >=, <, <=, ==, !=)")
    void testAllOperators() {
        assertTrue(RuleExpression.parse("val > 10").evaluate(new Message(Map.of("val", 11))));
        assertTrue(RuleExpression.parse("val >= 10").evaluate(new Message(Map.of("val", 10))));
        assertTrue(RuleExpression.parse("val < 10").evaluate(new Message(Map.of("val", 9))));
        assertTrue(RuleExpression.parse("val <= 10").evaluate(new Message(Map.of("val", 10))));
        assertTrue(RuleExpression.parse("val == 10").evaluate(new Message(Map.of("val", 10.0))));
        assertTrue(RuleExpression.parse("val != 10").evaluate(new Message(Map.of("val", 11))));
    }

    @Test
    @DisplayName("4. 잘못된 표현식 예외 발생 확인")
    void testInvalidExpression() {
        assertThrows(IllegalArgumentException.class, () -> RuleExpression.parse("temperature>30.0"));
        assertThrows(IllegalArgumentException.class, () -> RuleExpression.parse("temperature >"));
    }

    @Test
    @DisplayName("5. 필드 없음 (evaluate 결과 false 확인)")
    void testMissingField() {
        RuleExpression expr = RuleExpression.parse("temperature > 30.0");
        Message emptyMsg = new Message(Map.of("otherField", 100));
        assertFalse(expr.evaluate(emptyMsg));
    }
}