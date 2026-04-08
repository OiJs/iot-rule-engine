package com.fbp.engine.node;


import com.fbp.engine.message.Message;
import java.util.HashMap;
import java.util.Map;

public class GeneratorNode extends AbstractNode {

    public GeneratorNode(String id) {
        super(id);
        addOutputPort("out");
    }

    public void generate(String key, Object value) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(key, value);
        send("out", new Message(payload));
    }

    @Override
    protected void onProcess(Message message) {}
}