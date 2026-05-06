package com.fbp.engine.node;

import com.fbp.engine.message.Message;

public class RoutingRule {
    private final String field;      // 비교할 페이로드 키
    private final String operator;   // 연산자 (==, >, <, EXISTS 등)
    private final Object value;      // 비교 대상 값
    private final String targetPort; // 매칭 시 보낼 출력 포트 이름

    public RoutingRule(String field, String operator, Object value, String targetPort) {
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.targetPort = targetPort;
    }

    public String getTargetPort() { return targetPort; }

    public boolean matches(Message message) {
        Object msgValue = message.get(field);
        if (msgValue == null) {
            return "EXISTS".equals(operator) ? false : false;
        }

        return switch (operator) {
            case "==" -> msgValue.equals(value);
            case "EXISTS" -> true;
            case ">" -> (msgValue instanceof Number n) && (value instanceof Number v) && n.doubleValue() > v.doubleValue();
            case "<" -> (msgValue instanceof Number n) && (value instanceof Number v) && n.doubleValue() < v.doubleValue();
            default -> false;
        };
    }
}