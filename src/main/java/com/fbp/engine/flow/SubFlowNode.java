package com.fbp.engine.flow;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.ErrorPort;
import com.fbp.engine.core.Flow;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import java.util.Map;
import lombok.Getter;

public class SubFlowNode extends AbstractNode {
    @Getter
    private final Flow internalFlow;
    private final Map<String, String> inputMapping;
    private final Map<String, String> outputMapping;

    public SubFlowNode(String id, Flow internalFlow,
                       Map<String, String> inputMapping,
                       Map<String, String> outputMapping) {
        super(id);
        this.internalFlow = internalFlow;
        this.inputMapping = inputMapping;
        this.outputMapping = outputMapping;

        inputMapping.keySet().forEach(this::addInputPort);
        outputMapping.values().forEach(this::addOutputPort);
    }

    @Override
    public void initialize() {
        setupOutputBridges();
        setupErrorPropagation();
        internalFlow.initialize();
        super.initialize();
    }

    private void setupOutputBridges() {
        outputMapping.forEach((internalAddress, externalPortName) -> {
            String[] parts = internalAddress.split(":");
            String intNodeId = parts[0];
            String intPortName = parts[1];

            String bridgeId = "bridge_to_" + externalPortName;
            AbstractNode bridgeNode = new AbstractNode(bridgeId) {
                @Override
                protected void onProcess(Message message) {
                    SubFlowNode.this.send(externalPortName, message);
                }
            };

            bridgeNode.addInputPort("in");
            internalFlow.addNode(bridgeNode);
            internalFlow.connect(intNodeId, intPortName, bridgeId, "in");
        });
    }

    private void setupErrorPropagation() {
        internalFlow.getNodes().values().forEach(node -> {

            if (node instanceof AbstractNode absNode) {

                absNode.getErrorPort().connect(new Connection("err-link-" + absNode.getId()) {

                    @Override
                    public void deliver(Message message) {
                        ErrorPort extErrorPort = SubFlowNode.this.getErrorPort();

                        if (extErrorPort.hasConnection()) {
                            extErrorPort.sendError(message);
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onProcess(Message message) {
        String incomingPort = message.get("inputPort");
        String internalAddress = inputMapping.get(incomingPort);

        if (internalAddress != null) {
            String[] parts = internalAddress.split(":");
            String targetNodeId = parts[0];
            String targetPortName = parts[1];

            AbstractNode targetNode = internalFlow.getNode(targetNodeId);
            if (targetNode != null) {
                InputPort internalPort = targetNode.getInputPort(targetPortName);
                if (internalPort != null) {
                    internalPort.receive(message);
                }
            }
        }
    }

    @Override
    public void shutdown() {
        internalFlow.shutdown();
        super.shutdown();
    }
}