package com.fbp.engine.core;

import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.node.AbstractNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flow는 노드(Node)들과 그들을 잇는 연결(Connection)들의 집합체
 * 데이터 처리의 최소 단위인 토폴로지를 정의
 * 플로우의 상태를 관리하고, 노드 간의 데이터 흐름을 설정
 * 순환 참조(Cycle) 여부를 검증하는 로직을 포함.
 */
public class Flow {

    /**
     * 플로우의 가동 상태를 나타냅니다.
     */
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

    /**
     * 플로우에 새로운 노드를 추가합니다.
     * @param node 추가할 노드 객체
     * @return 자기 자신 (Method Chaining 지원)
     */
    public Flow addNode(AbstractNode node) {
        nodes.put(node.getId(), node);
        if (collector != null) {
            node.setContext(id, collector);
        }
        return this;
    }

    /**
     * 특정 노드와 그 노드에 연결된 모든 와이어를 함께 제거합니다.
     * @param nodeId 제거할 노드 ID
     */
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


    /**
     * 플로우에서 특정 연결(Wire)을 제거합니다.
     * 출발지 노드의 출력 포트에서 해당 연결을 분리하고, 큐에 남은 메시지가 소진될 때까지 대기 후 삭제합니다.
     * @param connectionId 제거할 연결 ID
     */
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

    /**
     * 연결의 내부 큐가 비워질 때까지 최대 3초간 대기합니다.
     * @param conn 대기할 연결 객체
     */
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

    /**
     * 두 노드 사이를 로컬 큐(LocalConnection)로 연결합니다.
     * @param sourceNodeId 출발 노드 ID
     * @param sourcePort 출발 포트 명
     * @param targetNodeId 도착 노드 ID
     * @param targetPort 도착 포트 명
     * @return 자기 자신
     */
    public Flow connect(String sourceNodeId, String sourcePort,
                        String targetNodeId, String targetPort) {

        AbstractNode sourceNode = nodes.get(sourceNodeId);
        AbstractNode targetNode = nodes.get(targetNodeId);

        if (sourceNode == null || targetNode == null) {
            throw new IllegalArgumentException("노드를 찾을 수 없습니다: " + sourceNodeId + ", " + targetNodeId);
        }

        if (sourceNode.getOutputPort(sourcePort) == null) {
            throw new IllegalArgumentException("출력 포트를 찾을 수 없습니다: " + sourceNodeId + ":" + sourcePort);
        }
        if (targetNode.getInputPort(targetPort) == null) {
            throw new IllegalArgumentException("입력 포트를 찾을 수 없습니다: " + targetNodeId + ":" + targetPort);
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

    /**
     * 두 노드 사이를 주어진 Connection 객체(MQTT 브릿지 등)를 사용하여 연결합니다.
     * @param sourceNodeId 출발 노드 ID
     * @param sourcePort 출발 포트 명
     * @param targetNodeId 도착 노드 ID
     * @param targetPort 도착 포트 명
     * @param connection 주입할 연결 객체
     * @return 자기 자신
     */
    public Flow connect(String sourceNodeId, String sourcePort,
                        String targetNodeId, String targetPort,
                        Connection connection) {

        AbstractNode sourceNode = nodes.get(sourceNodeId);
        AbstractNode targetNode = nodes.get(targetNodeId);

        if (sourceNode == null || targetNode == null) {
            throw new IllegalArgumentException("노드를 찾을 수 없습니다: " + sourceNodeId + ", " + targetNodeId);
        }

        if (sourceNode.getOutputPort(sourcePort) == null) {
            throw new IllegalArgumentException("출력 포트를 찾을 수 없습니다: " + sourceNodeId + ":" + sourcePort);
        }
        if (targetNode.getInputPort(targetPort) == null) {
            throw new IllegalArgumentException("입력 포트를 찾을 수 없습니다: " + targetNodeId + ":" + targetPort);
        }

        sourceNode.getOutputPort(sourcePort).connect(connection);
        connection.setTarget(targetNode.getInputPort(targetPort));

        if (!connections.contains(connection)) {
            connections.add(connection);
        }

        return this;
    }

    /**
     * 플로우가 실행 가능한 유효한 상태인지 검증합니다.
     * 노드 존재 여부 및 순환 참조 감지를 수행합니다.
     * @return 발생한 에러 메시지 리스트
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (nodes.isEmpty()) {
            errors.add("노드가 1개 이상 있어야 합니다.");
        }

        detectCycle(errors);

        return errors;
    }

    /**
     * DFS 알고리즘을 사용하여 플로우 내에 순환 참조(Cycle)가 있는지 감지합니다.
     */
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

    /**
     * 플로우 내의 모든 노드를 초기화합니다.
     */
    public void initialize() {
        nodes.values().forEach(AbstractNode::initialize);
    }

    /**
     * 플로우를 가동 중지하고 모든 노드 및 연결 자원을 해제합니다.
     */
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