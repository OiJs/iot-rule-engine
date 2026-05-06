package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import com.fbp.engine.rule.RuleExpression;
import java.util.function.Predicate;

public class RuleNode extends AbstractNode{
    private final Predicate<Message> condition;

    public RuleNode(String id, Predicate<Message> condition) {
        super(id);
        this.condition = condition;

        addInputPort("in");
        addOutputPort("match");
        addOutputPort("mismatch");
    }

    public RuleNode(String id, String expression) {
        this(id, msg -> RuleExpression.parse(expression).evaluate(msg));
    }

    @Override
    protected void onProcess(Message message) {
        if(condition.test(message)) {
            send("match", message);
        } else {
            send("mismatch", message);
        }
    }
}
