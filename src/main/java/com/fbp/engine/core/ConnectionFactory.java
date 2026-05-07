package com.fbp.engine.core;

import com.fbp.engine.parser.TransportDefinition;

public class ConnectionFactory {

    /**
     * Transport 설정에 따라 Local 또는 MQTT 브릿지 커넥션을 생성합니다.
     */
    public static Connection create(String connectionId, TransportDefinition config, String topic) {
        if (config != null && "mqtt".equals(config.type())) {

            return new MqttBridgeConnection(
                    connectionId,
                    topic,
                    MqttPool.getClient(config.broker()),
                    config.qos()
            );
        }

        return new LocalConnection(connectionId);
    }
}