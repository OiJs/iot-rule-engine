package com.fbp.engine.engine;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.Flow.FlowState;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.core.FlowNotFoundException;
import com.fbp.engine.parser.FlowParser;
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

    public FlowManager(FlowEngine engine) {
        this.engine = engine;
    }

    public void addParser(FlowParser parser) {
        parsers.put(parser.getSupportedFormat().toLowerCase(), parser);
    }


    //TODO FlowID 반환 고려
    public String deploy(String format, InputStream inputStream) {
        FlowParser parser = parsers.get(format.toLowerCase());

        if(parser == null) {
            throw new RuntimeException("지원하지 않는 포맷");
        }

        Flow flow = parser.parse(inputStream);

        if (managedFlows.containsKey(flow.getId())) {
            remove(flow.getId());
        }

        engine.register(flow);
        engine.startFlow(flow.getId());
        managedFlows.put(flow.getId(), flow);

        return flow.getId();
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
