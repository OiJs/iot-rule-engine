package com.fbp.engine.metrics.event;

import java.util.Map;

public record DomainDataEvent(
    long timestamp,
    String flowId,
    String nodeId,
    String sensorName,
    double value,
    Map<String, String> tags
) implements MetricEvent {}
