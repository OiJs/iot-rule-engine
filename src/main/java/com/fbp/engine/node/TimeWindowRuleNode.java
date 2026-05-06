package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Predicate;

public class TimeWindowRuleNode extends AbstractNode{
    private final Predicate<Message> condition;
    private final long windowMs;
    private final int threshold;
    private final Queue<Long> eventTimestamps = new LinkedList<>();

    public TimeWindowRuleNode(String id, Predicate<Message> condition, long windowMs, int threshold) {
        super(id);
        this.condition = condition;
        this.windowMs = windowMs;
        this.threshold = threshold;

        addInputPort("in");
        addOutputPort("alert");
        addOutputPort("pass");
    }

    @Override
    protected void onProcess(Message message) {
        long currentTime = System.currentTimeMillis();

        if (condition.test(message)) {
            eventTimestamps.add(currentTime);
        }

        // 시간 창(Window)을 벗어난 오래된 기록 제거
        while (!eventTimestamps.isEmpty() && (currentTime - eventTimestamps.peek() > windowMs)) {
            eventTimestamps.poll();
        }

        // 이벤트 횟수가 기준(Threshold) 이상인지 판단
        if (eventTimestamps.size() >= threshold) {
            send("alert", message);
        } else {
            send("pass", message);
        }
    }
}
