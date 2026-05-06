package com.fbp.engine.node;

import com.fbp.engine.core.Flow;
import com.fbp.engine.message.Message;

public class ErrorHandlerNode extends AbstractNode{
    private final int maxRetries;
    private final Flow contextFlow;

    public ErrorHandlerNode(String id,int maxRetries, Flow contextFlow) {
        super(id);
        this.maxRetries = maxRetries;
        this.contextFlow = contextFlow;
        addInputPort("in");
        addOutputPort("dlq");
    }

    @Override
    protected void onProcess(Message message) {
        String originNodeId = message.get("error_origin_node");
        int retryCount = (int)message.getPayload().getOrDefault("retry_count", 0);

        if(retryCount < maxRetries) {
            System.out.println("[RETRY] " + originNodeId + " 노드 재시도 중... (" + (retryCount + 1) + "/" + maxRetries + ")");

            Message retryMessage = message.withEntry("retry_count", retryCount + 1);
            AbstractNode originNode = contextFlow.getNode(originNodeId);

            if(originNode != null) {
                originNode.process(retryMessage);
            }
        } else {
            System.err.println("[FATAL] 재시도 횟수 초과. Dead Letter로 전송.");
            send("dlq", message);
        }

    }
}
