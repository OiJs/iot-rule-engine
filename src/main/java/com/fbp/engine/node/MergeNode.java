package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class MergeNode extends AbstractNode{
    private final Queue<Message> pending1 = new LinkedList<>();
    private final Queue<Message> pending2 = new LinkedList<>();

    protected MergeNode(String id) {
        super(id);
        addInputPort("in-1");
        addInputPort("in-2");
        addOutputPort("out");
    }

    @Override
    protected synchronized void onProcess(Message message) {
        String portName = message.get("inputPort");

        if ("in-1".equals(portName)) {
            pending1.add(message);
        } else if ("in-2".equals(portName)) {
            pending2.add(message);
        }

        while (!pending1.isEmpty() && !pending2.isEmpty()) {
            processAndSend();
        }
    }

    private void processAndSend() {
        Message m1 = pending1.poll();
        Message m2 = pending2.poll();

        Map<String, Object> mergePayload = new HashMap<>();
        mergePayload.putAll(m1.getPayload());
        mergePayload.putAll(m2.getPayload());

        Message mergeMessage = new Message(mergePayload);
        send("out", mergeMessage);

        System.out.println("[" + getId() + "] 병합 완료: " + mergePayload.keySet());
    }
}
