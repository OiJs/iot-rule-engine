package com.fbp.engine.node.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.io.ProtocolNode;
import java.util.Map;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class MqttPublisherNode extends ProtocolNode {
    private static final String DEFAULT_BROKER = "tcp://localhost:1883";
    private static final String DEFAULT_TOPIC = "fbp/output";
    private static final int DEFAULT_QOS = 1;
    private static final boolean DEFAULT_RETAINED = false;

    private String brokerUrl;
    private String clientId;
    private String topic;
    private int qos;
    private boolean retained;

    private MqttClient client;
    private final ObjectMapper objectMapper;

    public MqttPublisherNode(String id, Map<String, Object> config) {
        super(id, config);
        this.objectMapper = new ObjectMapper();

        syncConfig(config);

        addInputPort("in");

        if (!config.containsKey("brokerUrl") || !config.containsKey("clientId")) {
            throw new IllegalArgumentException("[" + id + "] 필수 설정(brokerUrl, clientId)이 누락되었습니다.");
        }
    }

    /**
     * 필드와 설정 맵을 동기화합니다.
     */
    private void syncConfig(Map<String, Object> cfg) {
        this.brokerUrl = (String) cfg.getOrDefault("brokerUrl", DEFAULT_BROKER);
        this.clientId = (String) cfg.getOrDefault("clientId", "fbp-pub-" + getId());
        this.topic = (String) cfg.getOrDefault("topic", DEFAULT_TOPIC);

        // 숫자 및 불리언 타입 안전하게 변환
        this.qos = ((Number) cfg.getOrDefault("qos", DEFAULT_QOS)).intValue();

        Object retainedObj = cfg.getOrDefault("retained", DEFAULT_RETAINED);
        this.retained = (retainedObj instanceof Boolean) ? (Boolean) retainedObj : Boolean.parseBoolean(retainedObj.toString());

        this.config.put("brokerUrl", this.brokerUrl);
        this.config.put("topic", this.topic);
        this.config.put("qos", this.qos);
        this.config.put("retained", this.retained);
    }

    /**
     * [Hot-Reload] 런타임 설정 변경 시 호출됩니다.
     */
    @Override
    protected void onConfigUpdate(Map<String, Object> newConfig) {
        String oldBroker = this.brokerUrl;
        String oldClientId = this.clientId;

        syncConfig(newConfig);

        // 브로커 주소나 클라이언트 ID가 바뀌면 재연결이 필요합니다.
        if (!brokerUrl.equals(oldBroker) || !clientId.equals(oldClientId)) {
            System.out.println("[" + getId() + "] 브로커 접속 정보 변경 감지 -> 재연결 시도");
            shutdown();
            initialize();
        } else {
            // 토픽, QoS, Retained 변경은 onProcess에서 필드를 직접 참조하므로
            // 별도의 조치 없이 다음 메시지부터 즉시 반영됩니다 (Instant Hot-Reload).
            System.out.println("[" + getId() + "] 발행 옵션 업데이트 완료 (Topic: " + topic + ", QoS: " + qos + ")");
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
                System.err.println("[" + getId() + "] 브로커 연결 끊김: " + response.getReasonString());
                reconnect();
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {
                System.err.println("[" + getId() + "] MQTT 에러: " + e.getMessage());
            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) {}

            @Override
            public void deliveryComplete(IMqttToken iMqttToken) {}

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                System.out.println("[" + getId() + "] 발행자 브로커 연결 성공: " + serverURI);
            }

            @Override
            public void authPacketArrived(int i, MqttProperties mqttProperties) {}
        });

        client.connect(options);
    }

    @Override
    protected void disconnect() throws Exception {
        if (client != null) {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
            System.out.println("[" + getId() + "] MQTT 발행자 종료.");
        }
    }

    @Override
    protected void onProcess(Message message) {
        if (!isConnected()) {
            System.err.println("[" + getId() + "] 연결되지 않음. 발행을 취소합니다.");
            return;
        }

        try {
            // 맵에서 실시간 업데이트된 필드들을 직접 사용합니다.
            byte[] payload = objectMapper.writeValueAsBytes(message.getPayload());

            MqttMessage mqttMessage = new MqttMessage(payload);
            mqttMessage.setQos(this.qos);
            mqttMessage.setRetained(this.retained);

            client.publish(this.topic, mqttMessage);
            System.out.println("[" + getId() + "] 발행 완료 -> Topic: " + this.topic + " (QoS: " + qos + ")");

        } catch (JsonProcessingException e) {
            System.err.println("[" + getId() + "] JSON 변환 실패: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[" + getId() + "] 메시지 발행 중 오류: " + e.getMessage());
        }
    }
}