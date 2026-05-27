package com.fbp.engine.metrics;

import com.fbp.engine.metrics.event.MetricEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * MetricsCollector는 Hot Path(메시지 처리 스레드)와 Cold Path(집계 스레드) 사이의 버퍼 역할을 합니다.
 * 모든 계측 포인트는 {@code submit(MetricEvent)}을 통해 이벤트를 큐에 던지며, 
 * 백그라운드 스레드에서 {@code MetricsAggregator}를 호출하여 집계를 수행합니다.
 * 큐가 가득 찰 경우 이벤트를 드롭(Drop)하여 메인 로직의 성능을 보호합니다.
 */
@Slf4j
public class MetricsCollector {
    private static final int MAX_QUEUE_SIZE = 100000;
    private final BlockingQueue<MetricEvent> eventQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final LongAdder droppedEvents = new LongAdder();
    private final MetricsAggregator aggregator;
    private final Thread aggregatorThread;
    private volatile boolean running = true;

    public MetricsCollector(MetricsAggregator aggregator) {
        this.aggregator = aggregator;
        this.aggregatorThread = new Thread(this::processEvents, "metrics-aggregator");
        this.aggregatorThread.setDaemon(true);
        this.aggregatorThread.start();
    }

    /**
     * 메트릭 이벤트 비동기 큐에 제출
     * 큐가 가득 찬 경우 이벤트를 무시하고 드롭 카운터를 증가
     * @param event 제출할 메트릭 이벤트
     */
    public void submit(MetricEvent event) {
        if (!eventQueue.offer(event)) {
            droppedEvents.increment();
            if (droppedEvents.sum() % 1000 == 0) {
                log.warn("Metrics queue is full, dropped {} events", droppedEvents.sum());
            }
        }
    }

    /**
     * 백그라운드 루프: 큐에서 이벤트를 꺼내 Aggregator에 전달.
     */
    private void processEvents() {
        while (running || !eventQueue.isEmpty()) {
            try {
                MetricEvent event = eventQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event != null) {
                    aggregator.aggregate(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in metrics aggregator", e);
            }
        }
    }

    /**
     * 집계 스레드를 안전하게 종료합니다.
     */
    public void stop() {
        running = false;
        try {
            aggregatorThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 큐 용량 초과로 인해 유실된 이벤트 총합을 반환합니다.
     * @return 드롭된 이벤트 수
     */
    public long getDroppedEvents() {
        return droppedEvents.sum();
    }

    /**
     * 특정 플로우 내 모든 노드의 통계 스냅샷을 조회합니다.
     * @param flowId 조회할 플로우 ID
     * @return 노드 ID별 스냅샷 맵
     */
    public Map<String, NodeMetrics.NodeMetricsSnapshot> getFlowMetrics(String flowId) {
        var nodeStats = aggregator.getNodeStats().get(flowId);
        if (nodeStats == null) return Map.of();
        
        return nodeStats.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    var stats = e.getValue();
                    return new NodeMetrics.NodeMetricsSnapshot(
                            stats.processedCount.sum(),
                            stats.errorCount.sum(),
                            (long) stats.latencyHistogram.getMean(), // estimation
                            stats.latencyHistogram.getMean() / 1000.0
                    );
                }));
    }

    /**
     * 특정 노드의 통계 스냅샷을 조회합니다.
     * @param flowId 플로우 ID
     * @param nodeId 노드 ID
     * @return 노드 통계 스냅샷
     */
    public NodeMetrics.NodeMetricsSnapshot getSnapshot(String flowId, String nodeId) {
        var flow = aggregator.getNodeStats().get(flowId);
        if (flow == null) return null;
        var stats = flow.get(nodeId);
        if (stats == null) return null;
        
        return new NodeMetrics.NodeMetricsSnapshot(
                stats.processedCount.sum(),
                stats.errorCount.sum(),
                (long) stats.latencyHistogram.getMean(),
                stats.latencyHistogram.getMean() / 1000.0
        );
    }
}

