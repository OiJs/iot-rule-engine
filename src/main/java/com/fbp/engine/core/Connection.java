package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import com.fbp.engine.metrics.MetricsCollector;

/**
 * Connection 인터페이스는 노드 사이를 연결하는 메시지 전송 통로를 정의합니다.
 * BlockingQueue를 사용하는 로컬 연결이나 MQTT 브로커를 사용하는 브릿지 연결 등 
 * 다양한 전송 계층(Transport Layer) 구현이 이 인터페이스를 따릅니다.
 */
public interface Connection {
    /**
     * 연결의 고유 ID를 반환합니다.
     * @return 연결 ID
     */
    String getId();

    /**
     * 메시지를 연결 통로에 전달(Deliver)합니다. 
     * 로컬 큐의 경우 큐에 삽입하고, MQTT의 경우 브로커로 발행(Publish)합니다.
     * @param message 전달할 메시지 객체
     */
    void deliver(Message message);

    /**
     * 이 연결과 맞닿아 있는 목적지 입력 포트를 반환합니다.
     * @return 타겟 입력 포트
     */
    InputPort getTarget();

    /**
     * 이 연결과 맞닿을 목적지 입력 포트를 설정합니다.
     * @param target 설정할 입력 포트
     */
    void setTarget(InputPort target);

    /**
     * 연결의 맥락(플로우 ID 및 메트릭 수집기)을 설정합니다.
     * @param flowId 플로우 ID
     * @param collector 메트릭 수집기 객체
     */
    void setContext(String flowId, MetricsCollector collector);

    /**
     * 연결 통로에서 메시지를 하나 꺼내옵니다. (수신자용)
     * @return 메시지 객체 (없을 경우 대기 또는 null 반환)
     * @throws InterruptedException 대기 중 인터럽트 발생 시
     */
    default Message poll() throws InterruptedException{return null;}

    /**
     * 현재 내부 큐에 쌓여 대기 중인 메시지의 개수를 반환합니다.
     * @return 적체된 메시지 수
     */
    default int getQueueSize() {return 0;}

    /**
     * 연결을 종료하고 점유 중인 자원(큐, MQTT 클라이언트 등)을 해제합니다.
     */
    default void close() {}
}

