package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

//TODO 4-7
class ConnectionBlockingQueueTest {

    private LocalConnection connection;
    private Message messageA;
    private Message messageB;
    private Message messageC;

    @BeforeEach
    void setUp() {
        connection = new LocalConnection("test-conn");
        messageA = new Message(new HashMap<>() {{ put("order", "A"); }});
        messageB = new Message(new HashMap<>() {{ put("order", "B"); }});
        messageC = new Message(new HashMap<>() {{ put("order", "C"); }});
    }

    // 1. deliver-poll 기본 동작
    @Test
    void deliver후_poll로_꺼낼_수_있다() throws InterruptedException {
        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(connection.poll());
            latch.countDown();
        });
        consumer.start();

        connection.deliver(messageA);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals("A", received.get().get("order"));
    }

    // 2. 메시지 순서 보장 (FIFO)
    @Test
    void 세개_메시지를_deliver하면_FIFO_순서로_poll된다() throws InterruptedException {
        connection.deliver(messageA);
        connection.deliver(messageB);
        connection.deliver(messageC);

        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();
        AtomicReference<String> third = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(3);

        Thread consumer = new Thread(() -> {
            first.set(connection.poll().get("order"));
            latch.countDown();
            second.set(connection.poll().get("order"));
            latch.countDown();
            third.set(connection.poll().get("order"));
            latch.countDown();
        });
        consumer.start();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("A", first.get());
        assertEquals("B", second.get());
        assertEquals("C", third.get());
    }

    // 3. 멀티스레드 deliver-poll
    @Test
    void 별도_스레드에서_deliver하고_다른_스레드에서_poll로_수신한다() throws InterruptedException {
        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 소비자 스레드 — 먼저 poll 대기
        Thread consumer = new Thread(() -> {
            received.set(connection.poll());
            latch.countDown();
        });

        // 생산자 스레드 — 500ms 후 deliver
        Thread producer = new Thread(() -> {
            try { Thread.sleep(500); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            connection.deliver(messageA);
        });

        consumer.start();
        producer.start();

        assertTrue(latch.await(3, TimeUnit.SECONDS), "타임아웃 내에 메시지를 수신해야 함");
        assertNotNull(received.get());
        assertEquals("A", received.get().get("order"));
    }

    // 4. poll 대기 동작 — 메시지 도착까지 블로킹
    @Test
    void deliver_전에_poll을_호출한_스레드가_블로킹된다() throws InterruptedException {
        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(connection.poll()); // 블로킹 대기
            latch.countDown();
        });
        consumer.start();

        // 소비자가 블로킹 중인지 확인
        Thread.sleep(300);
        assertNull(received.get(), "아직 메시지가 없으므로 수신되지 않았어야 함");

        // 이제 deliver
        connection.deliver(messageA);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
    }

    // 5. 버퍼 크기 제한 — 3번째 deliver가 블로킹됨
    @Test
    void 버퍼크기_2인_Connection에_3번째_deliver는_블로킹된다() throws InterruptedException {
        LocalConnection limitedConn = new LocalConnection("limited", 2);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Boolean> wasBlocked = new AtomicReference<>(false);

        limitedConn.deliver(messageA); // 1번째
        limitedConn.deliver(messageB); // 2번째 — 버퍼 꽉 참

        // 3번째 deliver는 별도 스레드에서 — 블로킹 예상
        Thread producer = new Thread(() -> {
            started.countDown();
            limitedConn.deliver(messageC); // 블로킹
            wasBlocked.set(true);
        });
        producer.start();
        started.await();

        Thread.sleep(300);
        assertFalse(wasBlocked.get(), "버퍼가 꽉 찼으므로 블로킹 중이어야 함");

        // 소비자가 하나 꺼내면 블로킹 해제
        Thread consumer = new Thread(limitedConn::poll);
        consumer.start();

        producer.join(2000);
        assertTrue(wasBlocked.get(), "poll 후 블로킹이 해제되어야 함");
    }

    // 6. 버퍼 크기 조회
    @Test
    void deliver후_getBufferSize가_예상값과_일치한다() throws InterruptedException {
        assertEquals(0, connection.getQueueSize());

        connection.deliver(messageA);
        assertEquals(1, connection.getQueueSize());

        connection.deliver(messageB);
        assertEquals(2, connection.getQueueSize());

        // poll은 블로킹이므로 별도 스레드에서
        Thread consumer = new Thread(connection::poll);
        consumer.start();
        consumer.join(1000);

        assertEquals(1, connection.getQueueSize());
    }
}