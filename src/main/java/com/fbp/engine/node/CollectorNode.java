package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.ArrayList;
import java.util.List;

public class CollectorNode extends AbstractNode{

    private final List<Message> collected;

    public CollectorNode(String id) {
        super(id);
        this.collected = new ArrayList<>();
        addInputPort("in");
    }

    public List<Message> getCollected() {
        return new ArrayList<>(collected);    }

    @Override
    protected void onProcess(Message message) {
        collected.add(message);
    }
}
