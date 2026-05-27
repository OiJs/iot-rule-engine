package com.fbp.engine.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransportDefinition(
        String type,   // "local" 또는 "mqtt"
        String broker, // "tcp://localhost:1884"
        int qos        // 0, 1, 2
) {
    public TransportDefinition(
            @JsonProperty("type") String type,
            @JsonProperty("broker") String broker,
            @JsonProperty("qos") int qos,
            @JsonProperty("config") Map<String, Object> config
    ) {
        this(
            type == null ? "local" : type,
            (broker == null && config != null && config.get("broker") != null) ? (String) config.get("broker") : broker,
            qos < 0 ? 1 : qos
        );
    }
}