package com.fbp.engine.parser;

public record TransportDefinition(
        String type,   // "local" 또는 "mqtt"
        String broker, // "tcp://localhost:1884"
        int qos        // 0, 1, 2
) {
    public TransportDefinition {
        if (type == null) type = "local";
        if (qos < 0) qos = 1;
    }
}