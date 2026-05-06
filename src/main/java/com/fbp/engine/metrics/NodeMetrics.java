package com.fbp.engine.metrics;

import java.util.concurrent.atomic.LongAdder;
import lombok.Getter;

public class NodeMetrics {
    //TODO flowId
    //flowId param으로 받기? 특정 flow의 메트릭 통계 어떻게 조회 할지?
    //특정 노드의 Metric만 개별적으로 조회?
    @Getter
    private final String flowId;
    private final LongAdder processedCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    private final LongAdder totalDuration = new LongAdder();

    public NodeMetrics(String flowId) {
        this.flowId = flowId;
    }

    public void record(boolean success, long durationNano) {
        processedCount.increment();
        if(!success) {
            errorCount.increment();
        }

        totalDuration.add(durationNano);
    }

    public NodeMetricsSnapshot getSnapshot() {
        return new NodeMetricsSnapshot(
                processedCount.sum(),
                errorCount.sum(),
                totalDuration.sum(),
                calculateAvg()
        );
    }

    private double calculateAvg() {
        long count = processedCount.sum();
        return count == 0 ? 0.0 : (double)totalDuration.sum() / count;
    }

    public record NodeMetricsSnapshot(
            long processedCount,
            long errorCount,
            long totalDuration,
            double avgDuration
    ) {}
}
