package com.fbp.engine.parser;

import java.util.List;
import java.util.Map;

public record DomainMetricDefinition(
    String name,
    MetricSource source,
    String field,
    Map<String, String> tags,
    List<String> windows
) {
    public DomainMetricDefinition {
        windows = (windows != null) ? List.copyOf(windows) : List.of("1m", "1h", "1d");
    }
}
