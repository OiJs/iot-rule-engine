package com.fbp.engine.core;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * MqttPool은 시스템 내에서 사용되는 MQTT 클라이언트 연결을 관리하는 싱글톤 풀입니다.
 * 동일한 브로커 URL에 대해 하나의 {@link MqttAsyncClient} 인스턴스만 생성하여 공유함으로써 
 * 시스템 리소스를 절약하고 불필요한 네트워크 연결을 방지합니다.
 * 또한, 도착하는 모든 메시지를 등록된 토픽별 핸들러로 전달하는 글로벌 라우팅 기능을 제공합니다.
 */
public class MqttPool {
    // 브로커 URL별로 클라이언트를 캐싱합니다. (Key: "tcp://localhost:1883")
    private static final Map<String, MqttAsyncClient> clients = new ConcurrentHashMap<>();
    
    // 글로벌 메시지 핸들러 라우터 (Topic -> Handler)
    private static final Map<String, BiConsumer<String, MqttMessage>> globalHandlers = new ConcurrentHashMap<>();

    /**
     * 지정된 브로커 URL에 연결된 MQTT 클라이언트를 반환합니다. 
     * 해당 URL에 대한 클라이언트가 없으면 새로 생성하고 연결을 시도합니다.
     * @param brokerUrl 접속할 브로커의 URL (예: tcp://localhost:1883)
     * @return 연결된 MqttAsyncClient 인스턴스
     * @throws RuntimeException 연결 실패 시 발생
     */
    public static MqttAsyncClient getClient(String brokerUrl) {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            throw new IllegalArgumentException("시스템 브로커 URL이 유효하지 않습니다.");
        }

        return clients.computeIfAbsent(brokerUrl, url -> {
            try {
                // 고유한 클라이언트 ID 생성 (엔진 인스턴스 구분용)
                String clientId = "fbp-engine-" + UUID.randomUUID().toString().substring(0, 8);
                
                // 메모리 기반 퍼시스턴스 사용
                MqttAsyncClient client = new MqttAsyncClient(url, clientId, new MemoryPersistence());
                
                // 연결 옵션 설정 (자동 재연결 등)
                MqttConnectionOptions options = new MqttConnectionOptions();
                options.setAutomaticReconnect(true);
                options.setCleanStart(true);
                options.setConnectionTimeout(10);

                // 글로벌 콜백 설정: 도착한 메시지를 등록된 토픽별 핸들러로 라우팅
                client.setCallback(new MqttCallback() {
                    @Override
                    public void disconnected(MqttDisconnectResponse response) {}
                    @Override
                    public void mqttErrorOccurred(MqttException e) {}
                    @Override
                    public void messageArrived(String topic, MqttMessage msg) {
                        BiConsumer<String, MqttMessage> handler = globalHandlers.get(topic);
                        if (handler != null) {
                            handler.accept(topic, msg);
                        }
                    }
                    @Override
                    public void deliveryComplete(IMqttToken token) {}
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {}
                    @Override
                    public void authPacketArrived(int reasonCode, MqttProperties properties) {}
                });

                client.connect(options).waitForCompletion();
                System.out.println("[MqttPool] 시스템 브로커 연결 성공: " + url);
                return client;
            } catch (Exception e) {
                throw new RuntimeException("시스템 브로커 연결 실패: " + url, e);
            }
        });
    }

    /**
     * 특정 토픽에 대한 메시지 수신 핸들러를 등록합니다. 
     * {@link MqttBridgeConnection} 등에서 메시지 수신 시 호출될 로직을 정의할 때 사용합니다.
     * @param topic 구독할 토픽 이름
     * @param handler 메시지 수신 시 실행할 콜백 (topic, message) -> void
     */
    public static void addHandler(String topic, BiConsumer<String, MqttMessage> handler) {
        globalHandlers.put(topic, handler);
    }

    /**
     * 엔진 종료 시 관리 중인 모든 MQTT 연결을 안전하게 해제하고 캐시를 비웁니다.
     */
    public static void shutdown() {
        clients.values().forEach(client -> {
            try {
                if (client.isConnected()) {
                    client.disconnect().waitForCompletion();
                    client.close();
                }
            } catch (Exception ignored) {}
        });
        clients.clear();
        globalHandlers.clear();
        System.out.println("[MqttPool] 모든 시스템 브로커 연결이 종료되었습니다.");
    }
}