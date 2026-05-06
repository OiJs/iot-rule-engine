package com.fbp.engine.rule;

import com.fbp.engine.message.Message;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RuleExpression {
    private final String field;
    private final String operator;
    private final Object value;

    public static RuleExpression parse(String expression) {
        String parts[] = expression.trim().split("\\s+");

        if(parts.length != 3) {
            throw new IllegalArgumentException("잘못된 조건식 포맷: " + expression);
        }
        String field = parts[0];
        String operator = parts[1];
        String rawValue = parts[2];

        Object value;

        try {
            value = Double.parseDouble(rawValue);
        } catch (NumberFormatException e) {
            value = rawValue;
        }
        return new RuleExpression(field, operator, value);
    }

    public boolean evaluate(Message message) {
        Object fieldValue = message.getPayload().get(field);
        if(fieldValue == null) return false;

        if(fieldValue instanceof Number && value instanceof Number) {
            double v1 = ((Number) fieldValue).doubleValue();
            double v2 = ((Number) value).doubleValue();

            switch (operator) {
                case ">":  return v1 > v2;
                case ">=": return v1 >= v2;
                case "<":  return v1 < v2;
                case "<=": return v1 <= v2;
                case "==": return v1 == v2;
                case "!=": return v1 != v2;
            }
        }
        String s1 = fieldValue.toString();
        String s2 = value.toString();

        switch (operator) {
            case "==": return s1.equals(s2);
            case "!=": return !s1.equals(s2);
        }
        return false;
    }
}
