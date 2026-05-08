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

    public void removeNodeWithConnections(String nodeId) {
        // 해당 노드가 출발지이거나 목적지인 모든 연결 제거
        connections.removeIf(conn -> {
            // Connection ID(예: "timer:out->logger:in")에 노드 ID가 포함되어 있는지 확인
            boolean isRelated = conn.getId().contains(nodeId + ":");
            if (isRelated) {
                conn.close(); // 연결 종료 처리
            }
            return isRelated;
        });

        nodes.remove(nodeId);
    }


    public void removeConnection(String connectionId) {
        Connection targetConn = connections.stream()
                .filter(c -> c.getId().equals(connectionId))
                .findFirst()
                .orElse(null);

        if(targetConn == null) return;

        try {
            String sourcePart = connectionId.split("->")[0];
            String sourceNodeId = sourcePart.split(":")[0];
            String sourcePortName = sourcePart.split(":")[1];

            //출발지 노드의 출력 포트에서 해당 연결 분리 (새 메시지 유입 차단)
            AbstractNode sourceNode = nodes.get(sourceNodeId);
            if(sourceNode != null) {
                sourceNode.getOutputPort(sourcePortName).disconnect(targetConn);
            }
        } catch (Exception e) {
            System.err.println("커넥션 파싱 중 오류: " + e.getMessage());
        }

        waitForConnectionDrain(targetConn);

        //리소스 해제 및 리스트에서 최종 삭제
        targetConn.close();
        connections.remove(targetConn);
        System.out.println("[Connection 제거 완료] " + connectionId);
    }

    private void waitForConnectionDrain(Connection conn) {
        int retry = 0;
        while (conn.getQueueSize() > 0 && retry < 30) {
            try {
                Thread.sleep(100);
                retry++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void setCollector(MetricsCollector collector) {
        this.collector = collector;
        nodes.values().forEach(node -> node.setContext(this.id, collector));
    }

    public Flow connect(String sourceNodeId, String sourcePort,
                        String targetNodeId, String targetPort) {

        AbstractNode sourceNode = nodes.get(sourceNodeId);
        AbstractNode targetNode = nodes.get(targetNodeId);

        if (sourceNode == null || targetNode == null) {
            throw new IllegalArgumentException("노드를 찾을 수 없습니다: " + sourceNodeId + ", " + targetNodeId);
        }

        String from = sourceNodeId + ":" + sourcePort;
        String to = targetNodeId + ":" + targetPort;
        String autoId = from + "->" + to;

        Connection conn = new LocalConnection(autoId);

        sourceNode.getOutputPort(sourcePort).connect(conn);
        conn.setTarget(targetNode.getInputPort(targetPort));

        connections.add(conn);
        return this;
    }

    public Flow connect(String sourceNodeId, String sourcePort,
                        String targetNodeId, String targetPort,
                        Connection connection) {

        AbstractNode sourceNode = nodes.get(sourceNodeId);
        AbstractNode targetNode = nodes.get(targetNodeId);

        if (sourceNode == null || targetNode == null) {
            throw new IllegalArgumentException("노드를 찾을 수 없습니다: " + sourceNodeId + ", " + targetNodeId);
        }

        sourceNode.getOutputPort(sourcePort).connect(connection);
        connection.setTarget(targetNode.getInputPort(targetPort));

        if (!connections.contains(connection)) {
            connections.add(connection);
        }

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

        for (String nodeId : nodes.keySet()) {
            graph.put(nodeId, new ArrayList<>());
        }

        for (Connection conn : connections) {
            String connId = conn.getId();
            String[] parts = connId.split("->");
            if (parts.length == 2) {
                String sourceNode = parts[0].split(":")[0];
                String targetNode = parts[1].split(":")[0];
                if (graph.containsKey(sourceNode) && graph.containsKey(targetNode)) {
                    graph.get(sourceNode).add(targetNode);
                }
            }
        }

        Map<String, Integer> stateMap = new HashMap<>();
        for (String nodeId : nodes.keySet()) {
            stateMap.put(nodeId, 0);
        }

        for (String nodeId : nodes.keySet()) {
            if (stateMap.get(nodeId) == 0) {
                if (dfs(nodeId, graph, stateMap)) {
                    errors.add("순환 참조 감지");
                    break;
                }
            }
        }
    }

    private boolean dfs(String nodeId, Map<String, List<String>> graph,
                        Map<String, Integer> stateMap) {
        stateMap.put(nodeId, 1);

        for (String next : graph.get(nodeId)) {
            if (stateMap.get(next) == 1) return true;
            if (stateMap.get(next) == 0) {
                if (dfs(next, graph, stateMap)) return true;
            }
        }

        stateMap.put(nodeId, 2);
        return false;
    }

    public void initialize() {
        nodes.values().forEach(AbstractNode::initialize);
    }

    public void shutdown() {
        nodes.values().forEach(AbstractNode::shutdown);
        connections.forEach(Connection::close);
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