package com.fbp.engine.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.metrics.event.WireDeliverEvent;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;

/**
 * MqttBridgeConnection은 외부 MQTT 브로커를 전송 계층으로 사용하여 노드 간 메시지를 전달합니다.
 * 이 구현체를 사용하면 노드들이 서로 다른 프로세스나 물리적 서버에 위치하더라도 
 * 투명하게 메시지를 주고받을 수 있습니다.
 * deliver() 시에는 특정 토픽으로 메시지를 발행(Publish)하고,
 * 생성 시 등록된 토픽을 구독(Subscribe)하여 들어오는 메시지를 내부 큐에 적재합니다.
 */
@Slf4j
public class MqttBridgeConnection implements Connection {
    private final String id;
    private final String topic;
    private final MqttAsyncClient mqttClient; // 발행과 구독을 동시에 수행
    private final int qos;
    private final ObjectMapper mapper = new ObjectMapper();

    private final BlockingQueue<Message> internalQueue = new LinkedBlockingQueue<>();

    private InputPort target;
    
    private String flowId;
    private MetricsCollector collector;

    public MqttBridgeConnection(String id, String topic, MqttAsyncClient mqttClient, int qos) {
        this.id = id;
        this.topic = topic;
        this.mqttClient = mqttClient;
        this.qos = qos;
        setupSubscription(); // 생성 시점에 구독 시작
    }

    @Override
    public void setContext(String flowId, MetricsCollector collector) {
        this.flowId = flowId;
        this.collector = collector;
    }

    /**
     * [수신 로직] MQTT Subscribe -> MqttPool 핸들러 등록 -> 내부 큐 적재
     */
    private void setupSubscription() {
        try {
            // Paho v5 subscribe 버그(IndexOutOfBounds)를 피하기 위해 
            // 리스너 배열 파라미터 대신 MqttPool의 글로벌 라우터를 사용합니다.
            mqttClient.subscribe(new MqttSubscription[]{ new MqttSubscription(topic, this.qos) }).waitForCompletion();
            
            // 글로벌 핸들러 등록
            MqttPool.addHandler(topic, (t, m) -> {
                try {
                    Message msg = mapper.readValue(m.getPayload(), Message.class);
                    internalQueue.offer(msg);
                } catch (Exception e) {
                    log.error("[MqttBridge] 메시지 수신/파싱 실패 (Topic: {}): {}", topic, e.getMessage());
                }
            });

            log.info("[MqttBridge] 구독 성공 및 핸들러 등록 완료: {}", topic);
        } catch (Exception e) {
            throw new RuntimeException("MQTT 구독 설정 실패: " + topic, e);
        }
    }

    /**
     * [발행 로직] 메시지를 JSON으로 직렬화하여 지정된 MQTT 토픽으로 발행합니다.
     * @param message 전달할 메시지 객체
     */
    @Override
    public void deliver(Message message) {
        long bytes = 0;
        boolean dropped = false;
        try {
            byte[] payload = mapper.writeValueAsBytes(message);
            bytes = payload.length;
            MqttMessage mqttMsg = new MqttMessage(payload);
            mqttMsg.setQos(this.qos);

            // 외부 브로커로 즉시 전송
            mqttClient.publish(topic, mqttMsg);
        } catch (Exception e) {
            dropped = true;
            log.error("[MqttBridge] MQTT 발행 실패 [ID: {}]: {}", id, e.getMessage());
        } finally {
            if (collector != null && flowId != null) {
                collector.submit(new WireDeliverEvent(
                    System.currentTimeMillis(),
                    flowId,
                    id,
                    internalQueue.size(),
                    dropped,
                    bytes
                ));
            }
        }
    }

    /**
     * 브로커로부터 수신되어 내부 큐에 적체된 메시지를 하나 꺼내옵니다.
     * @return 수신된 메시지 객체
     */
    @Override
    public Message poll() throws InterruptedException {
        return internalQueue.take();
    }

    @Override
    public int getQueueSize() {
        return internalQueue.size();
    }

    @Override
    public String getId() { return id; }

    @Override
    public void setTarget(InputPort target) { this.target = target; }

    @Override
    public InputPort getTarget() { return target; }

    /**
     * 연결을 종료할 때 브로커 구독을 해지합니다.
     */
    @Override
    public void close() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.unsubscribe(topic);
            }
        } catch (Exception ignored) {}
    }
}