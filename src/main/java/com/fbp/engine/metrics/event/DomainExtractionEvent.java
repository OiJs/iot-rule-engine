package com.fbp.engine.metrics.event;

import com.fbp.engine.message.Message;

public record DomainExtractionEvent(
    long timestamp,
    String flowId,
    String nodeId,
    String portName,
    Message message
) implements MetricEvent {}
