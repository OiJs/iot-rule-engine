package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.metrics.event.WireDeliverEvent;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.Getter;
import lombok.Setter;

/**
 * LocalConnection은 동일한 JVM 프로세스 내에서 노드 간 메시지를 전달하기 위한 연결 구현체입니다.
 * 자바의 {@link BlockingQueue}를 기반으로 하며, 메모리 내에서 매우 빠른 전송 속도를 제공합니다.
 */
public class LocalConnection implements Connection{
    @Getter
    private final String id;
    private final BlockingQueue<Message> queue;
    @Setter
    @Getter
    private InputPort target;
    
    private String flowId;
    private MetricsCollector collector;

    public LocalConnection(String id) {
        this.id = id;
        this.queue = new LinkedBlockingQueue<>(1000);

    }

    /**
     * 지정된 용량을 갖는 로컬 연결을 생성합니다.
     * @param id 연결 ID
     * @param capacity 큐의 최대 수용량
     */
    public LocalConnection(String id, int capacity) {
        this.id = id;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public void setContext(String flowId, MetricsCollector collector) {
        this.flowId = flowId;
        this.collector = collector;
    }

    /**
     * 메시지를 내부 큐에 삽입합니다. 큐가 가득 찬 경우 삽입에 실패하며 
     * 메트릭 수집기에 드롭(Drop) 이벤트를 기록합니다.
     * @param message 전달할 메시지 객체
     */
    @Override
    public void deliver(Message message) {
        boolean delivered = false;
        try {
            queue.put(message);
            delivered = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (collector != null && flowId != null) {
            collector.submit(new WireDeliverEvent(
                System.currentTimeMillis(),
                flowId,
                id,
                queue.size(),
                !delivered,
                0 // TODO: estimate bytes
            ));
        }
    }

    /**
     * 내부 큐에서 메시지를 하나 꺼내옵니다. 큐가 비어있으면 데이터가 들어올 때까지 블로킹됩니다.
     * @return 꺼내온 메시지 객체
     */
    @Override
    public Message poll() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
    @Override
    public int getQueueSize() {
        return queue.size();
    }
}

