package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.function.Function;

public class TransformNode extends AbstractNode{
    private final Function<Message, Message> transformer;

    public TransformNode(String id, Function<Message, Message> transformer) {
        super(id);
        this.transformer = transformer;
        addInputPort("in");
        addOutputPort("out");
    }


    @Override
    protected void onProcess(Message message) {
        Message result = transformer.apply(message);

        if(result != null) {
            send("out", result);
        }
    }
}
