package com.fbp.engine.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;

@Slf4j
public class MqttBridgeConnection implements Connection {
    private final String id;
    private final String topic;
    private final MqttClient mqttClient; // 발행과 구독을 동시에 수행
    private final int qos;
    private final ObjectMapper mapper = new ObjectMapper();

    private final BlockingQueue<Message> internalQueue = new LinkedBlockingQueue<>();

    private InputPort target;

    public MqttBridgeConnection(String id, String topic, MqttClient mqttClient, int qos) {
        this.id = id;
        this.topic = topic;
        this.mqttClient = mqttClient;
        this.qos = qos;
        setupSubscription(); // 생성 시점에 구독 시작
    }

    /**
     * [수신 로직] MQTT Subscribe -> 내부 큐 적재
     */
    private void setupSubscription() {
        try {
            mqttClient.subscribe(topic, this.qos, (t, m) -> {
                try {
                    Message msg = mapper.readValue(m.getPayload(), Message.class);
                    // 브로커에서 메시지가 오면 큐에 넣음 (생산자 역할)
                    internalQueue.offer(msg);
                } catch (Exception e) {
                    log.error("[MqttBridge] 메시지 수신/파싱 실패: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("MQTT 구독 설정 실패: " + topic, e);
        }
    }

    /**
     * [발행 로직] deliver() -> MQTT Publish
     */
    @Override
    public void deliver(Message message) {
        try {
            byte[] payload = mapper.writeValueAsBytes(message);
            MqttMessage mqttMsg = new MqttMessage(payload);
            mqttMsg.setQos(this.qos);

            // 외부 브로커로 즉시 전송
            mqttClient.publish(topic, mqttMsg);
        } catch (Exception e) {
            log.error("[MqttBridge] MQTT 발행 실패 [ID: {}]: {}", id, e.getMessage());
        }
    }

    /**
     * [획득 로직] poll() -> 내부 큐에서 꺼내기
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

    @Override
    public void close() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.unsubscribe(topic);
            }
        } catch (Exception ignored) {}
    }
}