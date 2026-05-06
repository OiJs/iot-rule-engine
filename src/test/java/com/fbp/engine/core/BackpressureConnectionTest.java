package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackpressureConnectionTest {

    private InputPort mockTarget;
    private final int CAPACITY = 2;

    @BeforeEach
    void setUp() {
        mockTarget = mock(InputPort.class);
    }

    private Message createMessage(String content) {
        return new Message(Map.of("data", content));
    }

    @Test
    @DisplayName("1. Block 전략: 큐 가득 참 -> send()가 블로킹됨")
    void testBlockStrategy() throws InterruptedException {
        BackpressureStrategy blockStrategy = (queue, msg) -> {
            try {
                queue.put(msg);
                return true;
            } catch (InterruptedException e) {
                return false;
            }
        };

        BackpressureConnection conn = new BackpressureConnection("conn-1", CAPACITY, blockStrategy);

        conn.deliver(createMessage("m1"));
        conn.deliver(createMessage("m2"));

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> conn.deliver(createMessage("m3")));

        assertThrows(TimeoutException.class, () -> future.get(1, TimeUnit.SECONDS), "Block 전략 시 블로킹되어야 합니다.");
    }

    @Test
    @DisplayName("2. DropOldest 전략: 가장 오래된 메시지 제거 확인")
    void testDropOldestStrategy() {
        BackpressureStrategy dropOldest = (queue, msg) -> {
            queue.poll();
            return queue.offer(msg);
        };

        BackpressureConnection conn = new BackpressureConnection("conn-2", CAPACITY, dropOldest);
        
        conn.deliver(createMessage("m1"));
        conn.deliver(createMessage("m2"));
        conn.deliver(createMessage("m3"));

        assertEquals(0, conn.getDropCount().sum());
    }

    @Test
    @DisplayName("3. DropNewest 전략: 새 메시지가 버려짐")
    void testDropNewestStrategy() {
        BackpressureStrategy dropNewest = (queue, msg) -> false;

        BackpressureConnection conn = new BackpressureConnection("conn-3", CAPACITY, dropNewest);
        
        conn.deliver(createMessage("m1"));
        conn.deliver(createMessage("m2"));
        conn.deliver(createMessage("m3"));

        assertEquals(1, conn.getDropCount().sum(), "새 메시지가 버려져 드롭 카운트가 1 증가해야 함");
    }

    @Test
    @DisplayName("4. 전략 변경: 런타임에 전략 변경 후 적용 확인")
    void testStrategyChangeAtRuntime() {
        BackpressureConnection conn = new BackpressureConnection("conn-4", CAPACITY, (queue, msg) -> false); // 초기 DropNewest
        
        conn.deliver(createMessage("m1"));
        conn.deliver(createMessage("m2"));
        conn.deliver(createMessage("m3"));
        assertEquals(1, conn.getDropCount().sum());

        conn.setStrategy((queue, msg) -> {
            queue.poll();
            return queue.offer(msg);
        });

        conn.deliver(createMessage("m4"));
        assertEquals(1, conn.getDropCount().sum(), "전략 변경 후 추가 드롭이 발생하지 않아야 함");
    }

    @Test
    @DisplayName("5. 큐 크기 설정: 지정한 용량 적용 확인")
    void testQueueCapacity() {
        BackpressureConnection conn = new BackpressureConnection("conn-5", 5, (queue, msg) -> false);

        for (int i = 0; i < 5; i++) {
            conn.deliver(createMessage("m" + i));
        }
        assertEquals(0, conn.getDropCount().sum());

        conn.deliver(createMessage("m6"));
        assertEquals(1, conn.getDropCount().sum());
    }

    @Test
    @DisplayName("6. 드롭 카운트: 메트릭 정상 증가 확인")
    void testDropCountMetric() {
        BackpressureConnection conn = new BackpressureConnection("conn-6", 1, (queue, msg) -> false);
        
        conn.deliver(createMessage("m1"));
        conn.deliver(createMessage("m2"));
        conn.deliver(createMessage("m3"));
        conn.deliver(createMessage("m4"));

        assertEquals(3, conn.getDropCount().sum());
    }

    @Test
    @DisplayName("7. 멀티스레드: 동시 전송 시 데이터 손실 없음")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testMultiThreadedSend() throws InterruptedException {
        int threadCount = 10;
        int msgPerThread = 100;
        int totalCapacity = threadCount * msgPerThread;

        BackpressureConnection conn = new BackpressureConnection("conn-7", totalCapacity, (queue, msg) -> false);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < msgPerThread; j++) {
                    conn.deliver(createMessage("test"));
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, conn.getDropCount().sum(), "용량이 충분할 때 멀티스레드 환경에서도 드롭이 없어야 함");
    }
}