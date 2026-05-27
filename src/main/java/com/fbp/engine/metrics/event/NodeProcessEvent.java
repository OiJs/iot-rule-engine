package com.fbp.engine.metrics.event;

public record NodeProcessEvent(
    long timestamp,
    String flowId,
    String nodeId,
    boolean success,
    long durationNano,
    long inBytes,
    long outBytes
) implements MetricEvent {}
