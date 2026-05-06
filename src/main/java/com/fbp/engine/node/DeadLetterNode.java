package com.fbp.engine.node;

import com.fbp.engine.message.Message;

public class DeadLetterNode extends AbstractNode {
    public DeadLetterNode(String id) {
        super(id);
        addInputPort("in");
    }

    @Override
    protected void onProcess(Message message) {
        System.err.println("======= [DEAD LETTER QUEUE] =======");
        System.err.println("최종 실패 노드: " + message.get("error_origin_node"));
        System.err.println("원인: " + message.get("error_message"));
        System.err.println("메시지 ID: " + message.getId());
        System.err.println("===================================");
    }
}