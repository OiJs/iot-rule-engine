package com.fbp.engine.collector;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MessageCollector extends AbstractNode {
    private final List<Message> messages = new CopyOnWriteArrayList<>();

    public MessageCollector(String id) {
        super(id);
        addInputPort("in");
    }

    @Override
    protected void onProcess(Message message) {
        messages.add(message);
        System.out.println("[MessageCollector] 메시지 수집 완료!");
    }

    public List<Message> getMessages() {
        return messages;
    }
}