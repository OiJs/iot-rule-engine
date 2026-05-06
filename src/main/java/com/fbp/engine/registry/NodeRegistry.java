package com.fbp.engine.registry;

import com.fbp.engine.core.Node;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NodeRegistry {
    private final Map<String, NodeFactory> factories = new ConcurrentHashMap<>();

    public void register(String typeName, NodeFactory factory) {
        if(typeName == null || typeName.isBlank()) {
            throw new NodeRegistryException("type name is null");
        }
        if(factory == null) {
            throw new NodeRegistryException("factory is null");
        }
        if(factories.containsKey(typeName)) {
            throw new NodeRegistryException("Already registered type: " + typeName);
        }
        factories.put(typeName, factory);
    }

    public Node create(String typeName, String id, Map<String, Object> config) {
        if(typeName == null) {
            throw new NodeRegistryException("type name is null");
        }

        NodeFactory factory = factories.get(typeName);

        if(factory == null) {
            throw new NodeRegistryException("Unknown node type: " + typeName);
        }
        return factory.create(id, config);
    }

    public Set<String> getRegisteredTypes() {
        return new HashSet<>(factories.keySet());
    }

    public boolean isRegistered(String typeName) {
        if(typeName == null) {
            throw new NodeRegistryException("type name is null");
        }
        return factories.containsKey(typeName);
    }

}
