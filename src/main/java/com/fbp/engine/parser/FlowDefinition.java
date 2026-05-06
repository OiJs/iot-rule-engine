package com.fbp.engine.parser;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record FlowDefinition(
        String id,
        String name,
        String description,
        List<NodeDefinition> nodes,
        List<ConnectionDefinition> connections
) {
    public FlowDefinition {
        nodes = (nodes != null) ? List.copyOf(nodes) : List.of();
        connections = (connections != null) ? List.copyOf(connections) : List.of();
     }

     public NodeDefinition getNode(String nodeId) {
        return nodes.stream()
                .filter(n -> n.id().equals(nodeId))
                .findFirst()
                .orElse(null);
     }

    public void validate() {
        Set<String> nodeIds = nodes.stream()
                .map(NodeDefinition::id)
                .collect(Collectors.toSet());

        for (ConnectionDefinition conn : connections) {
            String fromNodeId = conn.from().split(":")[0];
            String toNodeId = conn.to().split(":")[0];

            if (!nodeIds.contains(fromNodeId)) {
                throw new IllegalArgumentException("Source node not found: " + fromNodeId);
            }
            if (!nodeIds.contains(toNodeId)) {
                throw new IllegalArgumentException("Target node not found: " + toNodeId);
            }
        }
    }
}
