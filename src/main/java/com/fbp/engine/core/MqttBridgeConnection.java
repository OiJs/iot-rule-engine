package com.fbp.engine.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;

public class MqttBridgeConnection implements Connection {
    private final String id;
    private final String topic;
    private final MqttClient mqttClient;
    private final int qos; // QoS 필드 추가
    private final BlockingQueue<Message> internalQueue = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private InputPort target;

    public MqttBridgeConnection(String id, String topic, MqttClient mqttClient, int qos) {
        this.id = id;
        this.topic = topic;
        this.mqttClient = mqttClient;
        this.qos = qos;
        setupSubscription();
    }

    private void setupSubscription() {
        try {
            mqttClient.subscribe(topic, this.qos, (t, m) -> {
                Message msg = mapper.readValue(m.getPayload(), Message.class);

                if (this.target != null) {
                    this.target.receive(msg);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("MQTT 구독 실패: " + topic, e);
        }
    }

    @Override
    public void deliver(Message message) {
        try {
            byte[] payload = mapper.writeValueAsBytes(message);

            MqttMessage mqttMsg = new MqttMessage(payload);
            mqttMsg.setQos(this.qos);

            mqttClient.publish(topic, mqttMsg);
        } catch (Exception e) {
            System.err.println("MQTT 발행 실패 [" + id + "]: " + e.getMessage());
        }
    }

    @Override
    public Message poll() throws InterruptedException {
        return internalQueue.take();
    }

    @Override
    public String getId() { return id; }

    @Override
    public void setTarget(InputPort target) { this.target = target; }

    @Override
    public int getQueueSize() { return internalQueue.size(); }

    @Override
    public void close() {
        try {
            if (mqttClient.isConnected()) {
                mqttClient.unsubscribe(topic);
            }
        } catch (Exception ignored) {}
    }
}