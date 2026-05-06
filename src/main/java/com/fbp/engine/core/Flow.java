package com.fbp.engine.core;

import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.node.AbstractNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Flow {

    public enum FlowState {RUNNING, STOPPED}

    private final String id;
    private final Map<String, AbstractNode> nodes;
    private final List<Connection> connections;
    private volatile FlowState state;
    private MetricsCollector collector;

    public Flow(String id) {
        this.id = id;
        this.nodes = new HashMap<>();
        this.connections = new ArrayList<>();
        this.state = FlowState.STOPPED;
    }

    public Flow addNode(AbstractNode node) {
        nodes.put(node.getId(), node);
        if (collector != null) {
            node.setContext(id, collector);
        }
        return this;
    }

    public void setCollector(MetricsCollector collector) {
        this.collector = collector;
        nodes.values().forEach(node -> node.setContext(this.id, collector));
    }

    public Flow connect(String sourceNodeId, String sourcePort,
                        String targetNodeId, String targetPort) {

        AbstractNode sourceNode = nodes.get(sourceNodeId);
        if(sourceNode == null) {
            throw new IllegalArgumentException("sour node not found: " + sourceNodeId);
        }

        AbstractNode targetNode = nodes.get(targetNodeId);
        if(targetNode == null) {
            throw new IllegalArgumentException("target node not found: " + targetNodeId);
        }

        if(sourceNode.getOutputPort(sourcePort) == null) {
            throw new IllegalArgumentException("source port not found: " + sourcePort);
        }

        if(targetNode.getInputPort(targetPort) == null) {
            throw new IllegalArgumentException("target port not found: " + targetPort);
        }

        String connId = sourceNodeId + ":" + sourcePort + "->" + targetNodeId + ":" + targetPort;
        Connection conn = new Connection(connId);

        sourceNode.getOutputPort(sourcePort).connect(conn);
        conn.setTarget(targetNode.getInputPort(targetPort));

        connections.add(conn);
        return this;
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (nodes.isEmpty()) {
            errors.add("노드가 1개 이상 있어야 합니다.");
        }

        detectCycle(errors);

        return errors;
    }

    private void detectCycle(List<String> errors) {
        Map<String, List<String>> graph = new HashMap<>();

        for(String nodeId : nodes.keySet()) {
            graph.put(nodeId, new ArrayList<>());
        }

        for(Connection conn : connections) {
            String connId = conn.getId();
            String[] parts = connId.split("->");
            if(parts.length == 2) {
                String sourceId = parts[0].split(":")[0];
                String targetId = parts[1].split(":")[0];
                graph.get(sourceId).add(targetId);
            }
        }

        Map<String, Integer> state = new HashMap<>();
        for(String nodeId : nodes.keySet()) {
            state.put(nodeId, 0);
        }

        for(String nodeId : nodes.keySet()) {
            if(state.get(nodeId) == 0) {
                if (dfs(nodeId, graph, state)) {
                    errors.add("순환 참조 감지");
                    break;
                }
            }
        }
    }
    private boolean dfs(String nodeId, Map<String, List<String>> graph,
                        Map<String, Integer> state) {
        state.put(nodeId, 1);

        for (String next : graph.get(nodeId)) {
            if (state.get(next) == 1) return true;
            if (state.get(next) == 0) {
                if (dfs(next, graph, state)) return true;
            }
        }

        state.put(nodeId, 2);
        return false;
    }


    public void initialize() {
        nodes.values().forEach(AbstractNode::initialize);
    }

    public void shutdown() {
        nodes.values().forEach(AbstractNode::shutdown);
    }

    public String getId() { return id; }

    public Map<String, AbstractNode> getNodes() { return nodes; }

    public List<Connection> getConnections() { return connections; }

    public AbstractNode getNode(String id) {
        return nodes.get(id);
    }

    public FlowState getFlowState() {
        return state;
    }

    public void setFlowState(FlowState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return String.format("[%s: %s]", id, state);
    }
}
