package com.fbp.engine.node;

import com.fbp.engine.core.Node;
import com.fbp.engine.core.DefaultInputPort;
import com.fbp.engine.core.DefaultOutputPort;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.core.OutputPort;
import com.fbp.engine.message.Message;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractNode implements Node {
    private final String id;
    private final Map<String, InputPort> inputPorts;
    private final Map<String, OutputPort> outputPorts;

    protected AbstractNode(String id) {
        this. id = id;
        this.inputPorts = new HashMap<>();
        this.outputPorts = new HashMap<>();
    }

    protected void addInputPort(String name) {
        inputPorts.put(name, new DefaultInputPort(name, this));
    }

    protected void addOutputPort(String name) {
        outputPorts.put(name, new DefaultOutputPort(name));
    }

    public InputPort getInputPort(String name) {
        return inputPorts.get(name);
    }

    public OutputPort getOutputPort(String name) {
        return outputPorts.get(name);
    }

    protected void send(String portName, Message message) {
        OutputPort port = outputPorts.get(portName);
        if (port != null) {
            port.send(message);
        } else {
            System.out.println("[" + id + "] 경고: OutputPort '" + portName + "' 없음");
        }
    }

    protected abstract void onProcess(Message message);

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void process(Message message) {
        System.out.println("[" + id + "] processing message...");
        onProcess(message);
        System.out.println("[" + id + "] done.");
    }

    @Override
    public void initialize() {
        System.out.println("[" + id + "] initialized.");
    }

    @Override
    public void shutdown() {
        System.out.println("[" + id + "] shutdown.");
    }
}
