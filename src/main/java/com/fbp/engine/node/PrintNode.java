package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.Objects;
import lombok.Getter;

//TODO 3-9
@Getter
public class PrintNode extends AbstractNode {
    public PrintNode(String id) {
        super(id);
        addInputPort("in");

    }

    @Override
    protected void onProcess(Message message) {
        System.out.println("[print-" + getId() +"] " + message);
    }
}
