package com.fbp.engine.node;

import com.fbp.engine.core.ErrorPort;
import com.fbp.engine.core.Node;
import com.fbp.engine.core.DefaultInputPort;
import com.fbp.engine.core.DefaultOutputPort;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.core.OutputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.metrics.event.DomainExtractionEvent;
import com.fbp.engine.metrics.event.NodeProcessEvent;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * AbstractNode는 모든 FBP 노드의 기본 클래스입니다.
 * 입력 포트와 출력 포트 관리, 메시지 처리 흐름(process), 에러 처리, 
 * 그리고 메트릭 수집을 위한 계측 로직을 포함합니다.
 * 새로운 노드를 만들려면 이 클래스를 상속받아 {@code onProcess(Message)} 메서드를 구현해야 합니다.
 */
@Getter
public abstract class AbstractNode implements Node {
    private final String id;
    private String flowId;
    protected Map<String, Object> config;
    private final Map<String, InputPort> inputPorts;
    private final Map<String, OutputPort> outputPorts;
    private MetricsCollector collector;
    private final ErrorPort errorPort;

    protected AbstractNode(String id) {
        this(id, new HashMap<>());
    }

    protected AbstractNode(String id, Map<String, Object> config) {
        this. id = id;
        this.config = config != null ? new HashMap<>(config) : new HashMap<>();
        this.inputPorts = new HashMap<>();
        this.outputPorts = new HashMap<>();
        this.errorPort = new ErrorPort("error");
    }

    /**
     * 노드가 속한 플로우의 맥락(ID 및 메트릭 수집기)을 설정합니다.
     * @param flowId 플로우 ID
     * @param collector 메트릭 수집기 객체
     */
    public void setContext(String flowId, MetricsCollector collector) {
        this.flowId = flowId;
        this.collector = collector;

    }

    public void addInputPort(String name) {
        inputPorts.put(name, new DefaultInputPort(name, this));
    }

    public void addOutputPort(String name) {
        outputPorts.put(name, new DefaultOutputPort(name));
    }

    public ErrorPort getErrorPort() {
        return errorPort;
    }

    public InputPort getInputPort(String name) {
        return inputPorts.get(name);
    }

    public OutputPort getOutputPort(String name) {
        return outputPorts.get(name);
    }

    /**
     * 특정 출력 포트를 통해 메시지를 전송합니다.
     * 전송 시 도메인 메트릭 추출을 위한 이벤트를 메트릭 수집기에 제출합니다.
     * @param portName 메시지를 보낼 포트 이름
     * @param message 전송할 메시지 객체
     */
    protected void send(String portName, Message message) {
        OutputPort port = outputPorts.get(portName);
        if (port != null) {
            if (collector != null && flowId != null) {
                collector.submit(new DomainExtractionEvent(
                    System.currentTimeMillis(),
                    flowId,
                    id,
                    portName,
                    message
                ));
            }
            port.send(message);
        } else {
            System.out.println("[" + id + "] Warning: OutputPort '" + portName + "' not found");
        }
    }

    /**
     * 하위 클래스에서 구현해야 할 핵심 비즈니스 로직입니다.
     * @param message 입력 포트로부터 수신된 메시지
     */
    protected abstract void onProcess(Message message);

    /**
     * 실행 중에 노드 설정을 변경합니다. 
     * 변경 성공 시 {@code onConfigUpdate} 콜백을 호출합니다.
     * @param newConfig 새로운 설정 맵
     */
    public void reconfigure(Map<String, Object> newConfig) {
        Map<String, Object> oldConfig = this.config;
        try {
            this.config = new HashMap<>(newConfig);
            onConfigUpdate(this.config);
            System.out.println("[" + id + "] Config updated successfully.");
        } catch (Exception e) {
            this.config = oldConfig;
            throw new RuntimeException("[" + id + "] Config update failed: " + e.getMessage());
        }
    }

    /**
     * 설정이 업데이트된 후 호출되는 콜백 메서드
     * 하위 클래스에서 필요 시 오버라이드하여 런타임 설정 반영 로직을 구현
     * @param newConfig 업데이트된 설정 맵
     */
    protected void onConfigUpdate(Map<String, Object> newConfig) { }

    @Override
    public String getId() {
        return id;
    }

    /**
     * 노드의 메시지 처리 엔트리 포인트
     * 처리 시간을 측정하고 성공/실패 여부를 메트릭 수집기에 기록
     * @param message 처리할 메시지 객체
     */
    @Override
    public void process(Message message) {
        long startTime = System.nanoTime();
        boolean success = false;

        try {
            onProcess(message);
            success = true;
        } catch (Exception e) {
            success = false;
            handleNodeError(message, e);
        } finally {
            if(collector != null && flowId != null) {
                long duration = System.nanoTime() - startTime;
                collector.submit(new NodeProcessEvent(
                        System.currentTimeMillis(),
                        flowId,
                        id,
                        success,
                        duration,
                        0, // TODO: estimate inBytes
                        0  // TODO: estimate outBytes
                ));
            }
        }
    }

    /**
     * 노드 처리 중 발생한 예외를 처리하고 에러 메시지를 생성하여 ErrorPort로 전송합니다.
     */
    private void handleNodeError(Message originalMessage, Exception e) {
        Message errorMessage = originalMessage
                .withEntry("error_origin_node", this.id)
                .withEntry("error_message", e.getMessage())
                .withEntry("error_type", e.getClass().getSimpleName())
                .withEntry("error_timestamp", java.time.LocalDateTime.now().toString());

        if (errorPort.hasConnection()) {
            errorPort.send(errorMessage);
        } else {
            System.err.println("[" + id + "] Critical Error (No ErrorPort connected): " + e.getMessage());
        }
    }

    @Override
    public void initialize() {
        System.out.println("[" + id + "] initialized.");
    }

    @Override
    public void shutdown() {
        System.out.println("[" + id + "] shutdown.");
    }
}

