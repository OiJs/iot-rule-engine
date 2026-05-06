package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import lombok.Getter;
import lombok.Setter;

/**
 * FlowManager에서 중앙 제어하는 구조에 최적화된 커넥션.
 * 스스로 스레드를 생성하지 않고, 메시지 큐 관리와 백프레셔 전략 실행만 담당합니다.
 */
public class BackpressureConnection extends Connection {

    private final LinkedBlockingQueue<Message> queue;

    @Setter
    private BackpressureStrategy strategy;

    @Getter
    private final LongAdder dropCount = new LongAdder();

    public BackpressureConnection(String id, int capacity, BackpressureStrategy strategy) {
        super(id, capacity);
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.strategy = strategy;
    }

    /**
     * 생산자(Node)가 메시지를 보낼 때 호출됩니다.
     * 큐가 가득 찬 경우 지정된 전략(Block, Drop 등)을 수행합니다.
     */
    @Override
    public void deliver(Message message) {
        if (!queue.offer(message)) {
            // 큐가 가득 찼을 때의 전략 실행
            boolean delivered = strategy.handleOverflow(queue, message);
            if (!delivered) {
                dropCount.increment();
            }
        }
    }

    /**
     * FlowManager의 워커 스레드가 메시지를 꺼내갈 때 사용합니다.
     * @return 큐의 헤드 메시지, 비어있으면 null 반환
     */
    @Override
    public Message poll() {
        return queue.poll();
    }

    /**
     * 필요한 경우 현재 쌓인 메시지 수를 확인하기 위한 메서드
     */
    public int getQueueSize() {
        return queue.size();
    }
}