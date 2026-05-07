package com.fbp.engine.engine;

import static org.slf4j.MDC.remove;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.ConnectionFactory;
import com.fbp.engine.core.Flow;
import com.fbp.engine.core.Flow.FlowState;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.core.FlowNotFoundException;
import com.fbp.engine.node.AbstractNode;
import com.fbp.engine.parser.ConnectionDefinition;
import com.fbp.engine.parser.FlowDefinition;
import com.fbp.engine.parser.FlowParser;
import com.fbp.engine.parser.NodeDefinition;
import com.fbp.engine.registry.NodeRegistry;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

public class FlowManager {
    @Getter
    private final FlowEngine engine;
    private final Map<String, FlowParser> parsers = new HashMap<>();
    private final Map<String, Flow> managedFlows = new ConcurrentHashMap<>();
    private final NodeRegistry nodeRegistry;

    public FlowManager(FlowEngine engine, NodeRegistry nodeRegistry) {
        this.engine = engine;
        this.nodeRegistry = nodeRegistry;
    }

    public void addParser(FlowParser parser) {
        parsers.put(parser.getSupportedFormat().toLowerCase(), parser);
    }


    //TODO FlowID 반환 고려
    public String deploy(String format, InputStream inputStream) {
        FlowParser parser = parsers.get(format.toLowerCase());
        if (parser == null) {
            throw new RuntimeException("지원하지 않는 포맷: " + format);
        }

        FlowDefinition def = parser.parseToDefinition(inputStream);
        def.validate();

        if (managedFlows.containsKey(def.id())) {
            remove(def.id());
        }

        Flow flow = assembleFlow(def);

        engine.register(flow);
        engine.startFlow(flow.getId());
        managedFlows.put(flow.getId(), flow);

        return flow.getId();
    }

    private Flow assembleFlow(FlowDefinition def) {
        Flow flow = new Flow(def.id());

        for (NodeDefinition nodeDef : def.nodes()) {

            AbstractNode node = (AbstractNode) nodeRegistry.create(nodeDef.type(), nodeDef.id(), nodeDef.config());
            flow.addNode(node);
        }

        for (ConnectionDefinition connDef : def.connections()) {
            String topic = String.format("fbp/%s/%s-%s",
                    def.id(),
                    connDef.from().replace(":", "."),
                    connDef.to().replace(":", "."));

            Connection conn = ConnectionFactory.create(
                    connDef.id(),
                    def.transport(),
                    topic
            );

            flow.connect(connDef.from(), connDef.to(), conn);
        }

        return flow;
    }

    public FlowState getStatus(String flowId) {
        Flow flow = managedFlows.get(flowId);
        if(flow == null) {
            throw new FlowNotFoundException("존재하지 않는 Flow");
        }

        return flow.getFlowState();
    }

    public List<Flow> list() {
        return managedFlows.values().stream().toList();
    }

    public List<Flow> getRunningFlows() {
        return managedFlows.values().stream()
                .filter(flow -> flow.getFlowState().equals(FlowState.RUNNING))
                .toList();
    }

    public void stop(String flowId) {
        Flow flow = managedFlows.get(flowId);

        if(flow == null) {
            throw new FlowNotFoundException("존재하지 않는 Flow");
        }

        if(flow.getFlowState().equals(FlowState.RUNNING)) {
            engine.stopFlow(flowId);
        }
    }

    public void restart(String flowId) {
        Flow flow = managedFlows.get(flowId);

        if(flow == null) {
            throw new FlowNotFoundException("존재하지 않는 Flow");
        }
        if(flow.getFlowState().equals(FlowState.STOPPED)) {
            engine.startFlow(flowId);
        }
    }

    public void remove(String flowId) {
        Flow flow = managedFlows.get(flowId);

        if(flow == null) {
            throw new FlowNotFoundException("존재하지 않는 Flow");
        }

        if(flow.getFlowState().equals(FlowState.RUNNING)) {
            engine.stopFlow(flowId);
        }
        engine.unRegister(flow);
        managedFlows.remove(flowId);
    }
}
