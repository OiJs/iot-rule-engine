package com.fbp.engine.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class DomainMetricsExtractor {
    private static final ObjectMapper mapper = new ObjectMapper();

    public Double extractValue(Object payload, String field) {
        if (payload == null || field == null) return null;
        try {
            JsonNode root;
            if (payload instanceof JsonNode jn) {
                root = jn;
            } else if (payload instanceof Map m) {
                root = mapper.valueToTree(m);
            } else {
                root = mapper.valueToTree(payload);
            }

            // Support both "field.subfield" and JSON pointer "/field/subfield"
            String pointer = field.startsWith("/") ? field : "/" + field.replace('.', '/');
            JsonNode valueNode = root.at(pointer);
            
            if (valueNode.isNumber()) {
                return valueNode.asDouble();
            } else if (valueNode.isTextual()) {
                try {
                    return Double.parseDouble(valueNode.asText());
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            log.debug("Failed to extract field {} from payload", field);
        }
        return null;
    }
}
