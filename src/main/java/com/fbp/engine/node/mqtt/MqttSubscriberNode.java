package com.fbp.engine.node.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.io.ProtocolNode;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class MqttSubscriberNode extends ProtocolNode {
    private static final String DEFAULT_BROKER = "tcp://localhost:1883";
    private static final String DEFAULT_TOPIC = "fbp/default";
    private static final int DEFAULT_QOS = 1;

    private String brokerUrl;
    private String clientId;
    private String topic;
    private int qos;
    private MqttClient client;
    private final ObjectMapper objectMapper;

    public MqttSubscriberNode(String id, Map<String, Object> config) {
        super(id, config);
        syncConfig(config);
        addOutputPort("out");
        this.objectMapper = new ObjectMapper();

        if (!config.containsKey("brokerUrl") || !config.containsKey("clientId") || !config.containsKey("topic")) {
            throw new IllegalArgumentException("MqttSubscriberNode에 필수 설정값이 누락되었습니다.");
        }
    }

    private void syncConfig(Map<String, Object> cfg) {
        this.brokerUrl = (String) cfg.getOrDefault("brokerUrl", DEFAULT_BROKER);
        this.clientId = (String) cfg.getOrDefault("clientId", "fbp-sub-" + getId());
        this.topic = (String) cfg.getOrDefault("topic", DEFAULT_TOPIC);
        this.qos = ((Number) cfg.getOrDefault("qos", DEFAULT_QOS)).intValue();

        this.config.put("brokerUrl", this.brokerUrl);
        this.config.put("topic", this.topic);
    }

    @Override
    protected void onConfigUpdate(Map<String, Object> newConfig) {
        // 이전 값들 백업
        String oldBroker = this.brokerUrl;
        String oldTopic = this.topic;
        int oldQos = this.qos; // [추가] 이전 QoS 저장

        // 새로운 설정으로 필드 동기화
        syncConfig(newConfig);

        // 브로커 주소가 바뀌면 소켓 레벨의 재연결 필요
        if (!brokerUrl.equals(oldBroker)) {
            System.out.println("[" + getId() + "] 브로커 주소 변경 -> 전체 재연결");
            shutdown();
            initialize();
        }
        // 토픽 혹은 QoS가 바뀌면 재구독 처리
        else if ((!topic.equals(oldTopic) || qos != oldQos) && isConnected()) {
            try {
                if (!topic.equals(oldTopic)) {
                    client.unsubscribe(oldTopic);
                }

                client.subscribe(topic, qos);
                System.out.println("[" + getId() + "] 구독 설정 변경 성공 (Topic: " + topic + ", QoS: " + qos + ")");
            } catch (Exception e) {
                System.err.println("[" + getId() + "] 재구독 중 오류 발생: " + e.getMessage());
                reconnect();
            }
        }
    }


    @Override
    protected void connect() throws Exception {
        client = new MqttClient(brokerUrl, clientId, null);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setAutomaticReconnect(true);

        client.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse response) {
                System.err.println("[" + getId() + "] 연결 끊김: " + response.getReasonString());
                reconnect();
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {
                System.err.println("[" + getId() + "] MQTT 에러: " + e.getMessage());
            }

            @Override
            public void messageArrived(String t, MqttMessage msg) throws Exception {
                String rawPayload = new String(msg.getPayload());
                Map<String, Object> payloadMap;

                try {
                    payloadMap = objectMapper.readValue(rawPayload, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    System.err.println("[" + getId() + "] JSON 파싱 실패 원본 데이터: " + rawPayload);
                    payloadMap = new HashMap<>();
                    payloadMap.put("rawPayload", rawPayload);
                    payloadMap.put("error", "JSON Parsing Failed");
                }

                payloadMap.put("topic", t);
                payloadMap.put("mqttTimestamp", System.currentTimeMillis());

                Message fbpMessage = new Message(payloadMap);
                send("out", fbpMessage);

                System.out.println("[" + getId() + "] 메시지 수신 및 전달 완료 (Topic: " + t + ")");
            }

            @Override
            public void deliveryComplete(IMqttToken token) {}

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                System.out.println("[" + getId() + "] 브로커 연결 성공: " + serverURI);
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {}
        });

        client.connect(options);
        client.subscribe(topic, qos);
        System.out.println("[" + getId() + "] '" + topic + "' 구독 시작 (QoS: " + qos + ")");
    }

    @Override
    protected void disconnect() throws Exception {
        if (client != null) {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
            System.out.println("[" + getId() + "] MQTT 클라이언트 종료.");
        }
    }
}