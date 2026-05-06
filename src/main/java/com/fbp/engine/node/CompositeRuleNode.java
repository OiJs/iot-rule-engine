package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import com.fbp.engine.rule.RuleExpression;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CompositeRuleNode extends AbstractNode{
    public enum Operator {AND, OR}

    private final Operator operator;
    private final List<Predicate<Message>> conditions = new ArrayList<>();

    public CompositeRuleNode(String id, Operator operator) {
        super(id);
        this.operator = operator;

        addInputPort("in");
        addOutputPort("match");
        addOutputPort("mismatch");
    }

    public void addCondition(Predicate<Message> condition) {
        this.conditions.add(condition);
    }

    public void addCondition(String field, String op, Object value) {
        String expression = String.format("%s %s %s", field, op, value);
        this.conditions.add(msg -> RuleExpression.parse(expression).evaluate(msg));
    }

    @Override
    protected void onProcess(Message message) {
        boolean result;
        if (conditions.isEmpty()) {
            result = (operator == Operator.AND);
        } else if (operator == Operator.AND) {
            result = conditions.stream().allMatch(c -> c.test(message));
        } else {
            result = conditions.stream().anyMatch(c -> c.test(message));
        }

        if (result) {
            send("match", message);
        } else {
            send("mismatch", message);
        }
    }
}
