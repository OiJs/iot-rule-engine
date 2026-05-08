package com.fbp.engine.parser;

public record ConnectionDefinition(
        String from,
        String to
) {
    public String toAutoId() {
        return from + "->" + to;
    }
}
