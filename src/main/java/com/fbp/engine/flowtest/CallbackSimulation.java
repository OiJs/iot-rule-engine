package com.fbp.engine.flowtest;

import com.fbp.engine.listener.MessageListener;
import com.fbp.engine.message.Message;
import java.util.Map;

public class CallbackSimulation {

    public static void main(String[] args) {
        
        // 1. 리스너(콜백) 구현체 생성
        MessageListener listener = new MessageListener() {
            @Override
            public void onMessage(String topic, byte[] payload) {
                System.out.println("[콜백 수신] 외부 시스템이 데이터를 보냈습니다!");
                
                // 1단계: byte[] 배열을 다루기 쉬운 문자열로 변환 (JSON이라고 가정)
                String rawData = new String(payload);
                System.out.println(" ↳ 수신된 원본 데이터: " + rawData);

                // 2단계: FBP 엔진용 Message 객체로 포장 (불변 객체 생성)
                Message fbpMessage = new Message(Map.of(
                        "topic", topic,
                        "rawPayload", rawData,
                        "timestamp", System.currentTimeMillis()
                ));

                // 3단계: 노드의 OutputPort로 전송 (여기서는 가상 출력)
                // 실제 노드에서는 send("out", fbpMessage); 가 호출됩니다.
                System.out.println(" ↳ FBP Message 변환 완료: " + fbpMessage.getPayload());
                System.out.println(" ↳ 다음 노드로 전송 (send 호출)");
            }

            @Override
            public void onConnectionLost(Throwable cause) {
                System.err.println("[콜백 수신] 비상! 연결이 끊어졌습니다: " + cause.getMessage());
                // 실제 노드에서는 상태를 ERROR로 바꾸고 재연결 로직을 트리거해야 합니다.
            }
        };

        // --- 여기서부터는 외부 네트워크 라이브러리가 한다고 상상해 보세요! ---
        System.out.println("--- 📡 외부 라이브러리 동작 시뮬레이션 시작 ---");
        
        // 시나리오 1: 센서 데이터 정상 수신
        String incomingTopic = "sensor/livingroom/temp";
        byte[] incomingBytes = "{\"value\": 24.5, \"unit\": \"°C\"}".getBytes();
        
        // 외부 라이브러리가 우리가 등록한 리스너의 메서드를 대신 찔러줍니다.
        listener.onMessage(incomingTopic, incomingBytes);

        System.out.println("----------------------------------------------");

        // 시나리오 2: 갑작스러운 랜선 뽑힘
        listener.onConnectionLost(new RuntimeException("SocketTimeout: 서버가 응답하지 않습니다."));
    }
}