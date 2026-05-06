package com.fbp.engine.test;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@ToString
@Getter
public class PerformanceResult {
    private final long totalMessages;
    private final double throughput; // msgs/sec
    private final double avgLatency;  // ms
    private final long p99Latency;   // ms
    private final long errors;
    private final double errorRate;
}