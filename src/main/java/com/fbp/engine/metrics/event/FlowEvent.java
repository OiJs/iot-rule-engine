package com.fbp.engine.metrics.event;

public record FlowEvent(
    long timestamp,
    String flowId,
    String eventType,
    String summary
) implements MetricEvent {}
