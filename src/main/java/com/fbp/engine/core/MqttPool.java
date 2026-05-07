package com.fbp.engine.core;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 시스템 브로커 연결을 관리하는 싱글톤 풀입니다.
 * 브로커 URL 하나당 하나의 MQTT 클라이언트만 유지하여 리소스를 절약합니다.
 */
public class MqttPool {
    // 브로커 URL별로 클라이언트를 캐싱합니다. (Key: "tcp://localhost:1884")
    private static final Map<String, MqttClient> clients = new ConcurrentHashMap<>();

    public static MqttClient getClient(String brokerUrl) {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            throw new IllegalArgumentException("시스템 브로커 URL이 유효하지 않습니다.");
        }

        return clients.computeIfAbsent(brokerUrl, url -> {
            try {
                // 고유한 클라이언트 ID 생성 (엔진 인스턴스 구분용)
                String clientId = "fbp-engine-" + UUID.randomUUID().toString().substring(0, 8);
                
                // 메모리 기반 퍼시스턴스 사용
                MqttClient client = new MqttClient(url, clientId, new MemoryPersistence());
                
                // 연결 옵션 설정 (자동 재연결 등)
                MqttConnectionOptions options = new MqttConnectionOptions();
                options.setAutomaticReconnect(true);
                options.setCleanStart(true);
                options.setConnectionTimeout(10);

                client.connect(options);
                System.out.println("[MqttPool] 시스템 브로커 연결 성공: " + url);
                return client;
            } catch (Exception e) {
                throw new RuntimeException("시스템 브로커 연결 실패: " + url, e);
            }
        });
    }

    /**
     * 엔진 종료 시 모든 MQTT 연결을 해제합니다.
     */
    public static void shutdown() {
        clients.values().forEach(client -> {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                    client.close();
                }
            } catch (Exception ignored) {}
        });
        clients.clear();
        System.out.println("[MqttPool] 모든 시스템 브로커 연결이 종료되었습니다.");
    }
}