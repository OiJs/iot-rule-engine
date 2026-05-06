package com.fbp.engine.parser;

import java.util.Map;

public record NodeDefinition(
        String id,
        String type,
        Map<String, Object> config
) {}
