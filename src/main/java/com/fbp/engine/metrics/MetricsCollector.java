package com.fbp.engine.metrics;

import com.fbp.engine.metrics.NodeMetrics.NodeMetricsSnapshot;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MetricsCollector {
    private final Map<String, Map<String, NodeMetrics>> flowStats = new ConcurrentHashMap<>();

    /**
     *  처리 및 에러 건수 기록
     * @param flowId 플로우 식별자
     * @param nodeId 노드 식별자
     * @param success 성공 여부
     * @param duration 소요 시간
     */
    public void recordProcessing(String flowId, String nodeId, boolean success, long duration) {
        flowStats.computeIfAbsent(flowId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(nodeId, k -> new NodeMetrics(flowId))
                .record(success, duration);
    }


    /**
     * 특정 노드의 metrics 반환 없는 id조회 시 null 반환
     * @param nodeId
     * @return 해당 노드의 스냅샷 or null
     */
    public NodeMetricsSnapshot getSnapshot(String flowId, String nodeId) {
        Map<String, NodeMetrics> nodes = flowStats.get(flowId);
        if(nodes == null) return null;

        NodeMetrics metrics = nodes.get(nodeId);
        return (metrics != null) ? metrics.getSnapshot() : null;
    }

    public Map<String, NodeMetricsSnapshot> getFlowMetrics(String flowId) {
        Map<String, NodeMetrics> nodes = flowStats.get(flowId);
        if (nodes == null) return Map.of();

        return nodes.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getSnapshot()));
    }

    public void reset() {
        flowStats.clear();
    }
}
