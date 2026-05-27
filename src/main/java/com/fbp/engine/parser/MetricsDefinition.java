package com.fbp.engine.parser;

import java.util.List;
import java.util.Map;

public record MetricsDefinition(
    List<DomainMetricDefinition> domain
) {
    public MetricsDefinition {
        domain = (domain != null) ? List.copyOf(domain) : List.of();
    }
}
