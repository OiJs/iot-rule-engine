package com.fbp.engine.listener;

public interface MessageListener {

    /**
     * 데이터가 수신되었을 때 호출됩니다.
     * @param topic   데이터의 주제 (MQTT 토픽이나 MODBUS 레지스터 주소 등)
     * @param payload 실제 데이터 원본 (바이트 배열)
     */
    void onMessage(String topic, byte[] payload);

    /**
     * 네트워크 연결이 예기치 않게 끊어졌을 때 호출
     * @param cause 끊김의 원인이 된 예외 객체
     */
    void onConnectionLost(Throwable cause);
}
