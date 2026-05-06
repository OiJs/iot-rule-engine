package com.fbp.engine.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.core.Flow;
import com.fbp.engine.core.Node;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.registry.NodeRegistry;
import java.io.IOException;
import java.io.InputStream;

public class JsonFlowParser implements FlowParser{
    private final NodeRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonFlowParser(NodeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Flow parse(InputStream inputStream) {

        try {
            FlowDefinition def = objectMapper.readValue(inputStream, FlowDefinition.class);

            if (def.id() == null || def.id().isBlank()) {
                throw new FlowParserException("Flow ID is missing");
            }

            if (def.nodes() == null || def.nodes().isEmpty()) {
                throw new FlowParserException("Nodes list is missing or empty");
            }

            Flow flow = new Flow(def.id());

            for(NodeDefinition nd : def.nodes()) {
                if(flow.getNode(nd.id()) != null) {
                    throw new FlowParserException("Duplicate node ID: " + nd.id());                }
                Node node = registry.create(nd.type(), nd.id(), nd.config());
                flow.addNode((AbstractNode) node);
            }

            if(def.connections() != null) {
                for (ConnectionDefinition cd : def.connections()) {
                    try {
                        wire(flow, cd);
                    } catch (Exception e) {
                        throw new FlowParserException("Failed to wire connections: " + e.getMessage());
                    }
                }
            }
            return flow;
        } catch (IOException e) {
            throw new FlowParserException("플로우 파일 읽기 실패" + e);
        }
    }

    @Override
    public String getSupportedFormat() {
        return "json";
    }

    private void wire(Flow flow, ConnectionDefinition cd) {
        String[] fromParts = cd.from().trim().split(":");
        String[] toParts = cd.to().trim().split(":");

        if(fromParts.length < 2 || toParts.length < 2) {
            throw new FlowParserException("잘못된 연결 형식: " + cd.from() + " -> " + cd.to());
        }
        flow.connect(fromParts[0], fromParts[1], toParts[0], toParts[1]);
    }
}
