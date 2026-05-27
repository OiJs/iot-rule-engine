package com.fbp.engine.metrics.event;

public sealed interface MetricEvent permits 
    NodeProcessEvent, 
    WireDeliverEvent, 
    DomainDataEvent,
    DomainExtractionEvent,
    FlowEvent {
    
    long timestamp();
    String flowId();
}
