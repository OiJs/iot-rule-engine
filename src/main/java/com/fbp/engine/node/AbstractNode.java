package com.fbp.engine.node;

import com.fbp.engine.core.ErrorPort;
import com.fbp.engine.core.Node;
import com.fbp.engine.core.DefaultInputPort;
import com.fbp.engine.core.DefaultOutputPort;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.core.OutputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.metrics.MetricsCollector;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public abstract class AbstractNode implements Node {
    private final String id;
    private String flowId;
    protected Map<String, Object> config;
    private final Map<String, InputPort> inputPorts;
    private final Map<String, OutputPort> outputPorts;
    private MetricsCollector collector;
    private final ErrorPort errorPort;

    protected AbstractNode(String id) {
        this(id, new HashMap<>());
    }

    protected AbstractNode(String id, Map<String, Object> config) {
        this. id = id;
        this.config = config != null ? new HashMap<>(config) : new HashMap<>();
        this.inputPorts = new HashMap<>();
        this.outputPorts = new HashMap<>();
        this.errorPort = new ErrorPort("error");
    }

    public void setContext(String flowId, MetricsCollector collector) {
        this.flowId = flowId;
        this.collector = collector;

    }

    public void addInputPort(String name) {
        inputPorts.put(name, new DefaultInputPort(name, this));
    }

    public void addOutputPort(String name) {
        outputPorts.put(name, new DefaultOutputPort(name));
    }

    public ErrorPort getErrorPort() {
        return errorPort;
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

    public void reconfigure(Map<String, Object> newConfig) {
        Map<String, Object> oldConfig = this.config;
        try {
            this.config = new HashMap<>(newConfig);
            onConfigUpdate(this.config);
            System.out.println("[" + id + "] Config updated successfully.");
        } catch (Exception e) {
            this.config = oldConfig;
            throw new RuntimeException("[" + id + "] Config update failed: " + e.getMessage());
        }
    }

    protected void onConfigUpdate(Map<String, Object> newConfig) { }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void process(Message message) {
        long startTime = System.nanoTime();
        boolean success = false;

        try {
            onProcess(message);
            success = true;
        } catch (Exception e) {
            success = false;
            handleNodeError(message, e);
        } finally {
            if(collector != null && flowId != null) {
                long duration = System.nanoTime() - startTime;
                collector.recordProcessing(flowId, id, success, duration);
            }
        }
    }

    private void handleNodeError(Message originalMessage, Exception e) {
        Message errorMessage = originalMessage
                .withEntry("error_origin_node", this.id)
                .withEntry("error_message", e.getMessage())
                .withEntry("error_type", e.getClass().getSimpleName())
                .withEntry("error_timestamp", java.time.LocalDateTime.now().toString());

        if (errorPort.hasConnection()) {
            errorPort.send(errorMessage);
        } else {
            System.err.println("[" + id + "] Critical Error (No ErrorPort connected): " + e.getMessage());
        }
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
