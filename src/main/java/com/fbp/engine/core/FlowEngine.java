package com.fbp.engine.core;

import com.fbp.engine.core.Flow.FlowState;
import com.fbp.engine.message.Message;
import com.fbp.engine.metrics.*;
import com.fbp.engine.parser.FlowDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Getter;
import lombok.ToString;

/**
 * FlowEngine은 FBP 시스템의 실제 실행 엔진입니다.
 * 등록된 플로우(Flow)들을 관리하고, 각 플로우 내의 연결(Connection)을 처리하기 위한
 * 워커 스레드 풀을 운영합니다. 또한 메트릭 수집기(MetricsCollector)와 
 * InfluxDB 스케줄러를 초기화하고 생명주기를 관리합니다.
 */
@ToString
public class FlowEngine {

    /**
     * 엔진의 실행 상태를 나타냅니다.
     */
    public enum State {
        INITIALIZED, RUNNING, STOPPED
    }

    private final Map<String, Flow> flows;
    private State state;
    private final ExecutorService executorService;

    @Getter
    private final MetricsAggregator aggregator = new MetricsAggregator();
    private final InfluxBatchWriter influxWriter;
    private final MetricsScheduler metricsScheduler;
    @Getter
    private final MetricsCollector collector;

    public FlowEngine() {
        this(new InfluxConfig());
    }

    /**
     * InfluxDB 설정을 사용하여 엔진을 초기화합니다.
     * 비동기 메트릭 수집기와 스케줄러를 시작합니다.
     * @param config InfluxDB 연결 및 배치 설정
     */
    public FlowEngine(InfluxConfig config) {
        this.flows = new HashMap<>();
        this.state = State.INITIALIZED;
        this.executorService = Executors.newFixedThreadPool(10);

        this.influxWriter = new InfluxBatchWriter(config);
        this.metricsScheduler = new MetricsScheduler(aggregator, influxWriter);
        this.collector = new MetricsCollector(aggregator);

        this.metricsScheduler.start();
    }

    /**
     * 설계도 정보 없이 플로우를 등록합니다.
     * @param flow 등록할 플로우 객체
     */
    public void register(Flow flow) {
        register(flow, null);
    }

    /**
     * 플로우 객체와 설계도 정보를 함께 등록합니다. 
     * 설계도 정보는 도메인 메트릭(센서) 추출 설정을 등록하는 데 사용됩니다.
     * @param flow 등록할 플로우 객체
     * @param def 플로우 설계도 (Metrics 설정 포함)
     */
    public void register(Flow flow, FlowDefinition def) {
        if (def != null) {
            aggregator.registerFlow(def);
        }
        flow.setCollector(this.collector);
        flows.put(flow.getId(), flow);
        System.out.println("[Engine] Flow '" + flow.getId() + "' registered");
    }

    /**
     * 엔진에서 플로우 등록을 해제합니다.
     * @param flow 해제할 플로우 객체
     */
    public void unRegister(Flow flow) {
        flows.remove(flow.getId());
    }


    /**
     * 지정된 ID의 플로우를 검증하고 실행합니다.
     * 플로우 내의 모든 노드를 초기화하고, 연결(Wire)을 처리할 워커 스레드를 할당합니다.
     * @param flowId 실행할 플로우 ID
     */
    public void startFlow(String flowId) {
        Flow flow = flows.get(flowId);
        if (flow == null) {
            throw new IllegalArgumentException("Flow ID not found: " + flowId);
        }

        List<String> errors = flow.validate();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Flow validation failed: " + errors);
        }

        flow.initialize();

        // [수정] 재귀적으로 모든 커넥션(서브플로우 포함)을 찾아 스레드 풀에 등록
        registerConnectionWorkers(flow);

        flow.setFlowState(FlowState.RUNNING);
        this.state = State.RUNNING;
        System.out.println("[Engine] Flow '" + flowId + "' started");
    }

    /**
     * 플로우의 모든 연결(Connection)에 대해 메시지 루프 워커를 스레드 풀에 등록합니다.
     * 연결에서 메시지를 폴링(poll)하여 타겟 노드의 수신(receive) 메서드로 전달합니다.
     * @param flow 연결을 등록할 대상 플로우
     */
    private void registerConnectionWorkers(Flow flow) {
        for (Connection conn : flow.getConnections()) {
            executorService.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    Message msg = null;
                    try {
                        msg = conn.poll();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (msg != null && conn.getTarget() != null) {
                        conn.getTarget().receive(msg);
                    } else {
                        Thread.yield();
                    }
                }
            });
        }

        flow.getNodes().values().forEach(node -> {
            if (node instanceof com.fbp.engine.flow.SubFlowNode subflow) {
                registerConnectionWorkers(subflow.getInternalFlow());
            }
        });
    }

    /**
     * 실행 중인 플로우를 중지하고 모든 노드 자원을 해제합니다.
     * @param flowId 중지할 플로우 ID
     */
    public void stopFlow(String flowId) {
        Flow flow = flows.get(flowId);
        if (flow != null) {
            flow.shutdown();
            flow.setFlowState(FlowState.STOPPED);
            System.out.println("[Engine] Flow '" + flowId + "' stopped");
        }
    }

    /**
     * 엔진 전체를 종료합니다. 
     * 실행 중인 모든 플로우를 중지하고, 메트릭 수집기 및 DB 라이터를 안전하게 종료합니다.
     */
    public void shutdown() {
        for(Flow flow : flows.values()) {
            flow.shutdown();
        }
        this.state = State.STOPPED;
        metricsScheduler.stop();
        collector.stop();
        influxWriter.close();
        MqttPool.shutdown();
        executorService.shutdown();
    }

    public State getState() {
        return state;
    }

    public Map<String, Flow> getFlows() {
        return flows;
    }

    public List<Flow> listFlows() {
        return flows.values().stream().toList();
    }
}

