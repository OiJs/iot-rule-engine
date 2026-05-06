package com.fbp.engine.registry;

import com.fbp.engine.core.Node;
import java.util.Map;

@FunctionalInterface
public interface NodeFactory {
    Node create(String id, Map<String, Object> config);
}
