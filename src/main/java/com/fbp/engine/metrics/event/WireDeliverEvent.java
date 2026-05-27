package com.fbp.engine.metrics.event;

public record WireDeliverEvent(
    long timestamp,
    String flowId,
    String wireId,
    int queueSize,
    boolean dropped,
    long bytes
) implements MetricEvent {}
