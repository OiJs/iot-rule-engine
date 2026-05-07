package com.fbp.engine.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fbp.engine.message.Message;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectionTest {

    @Test
    @DisplayName("deliver한 메시지를 poll로 꺼낼 수 있어야 한다")
    void test1_DeliverThenPoll() throws InterruptedException {
        LocalConnection conn = new LocalConnection("c1");
        Message msg = new Message(Map.of("key", "test"));

        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(conn.poll());
            latch.countDown();
        });
        consumer.start();

        conn.deliver(msg);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(msg, received.get());
    }

    @Test
    @DisplayName("target 없이 deliver해도 예외가 발생하지 않아야 한다")
    void test2_DeliverWithoutTargetDoesNotThrow() {
        LocalConnection conn = new LocalConnection("c2");
        Message msg = new Message(Map.of("a", "b"));

        assertDoesNotThrow(() -> conn.deliver(msg));
    }

    @Test
    @DisplayName("deliver 후 버퍼 크기가 올바르게 반영되어야 한다")
    void test3_BufferCount() {
        LocalConnection conn = new LocalConnection("c3");

        conn.deliver(new Message(Map.of("id", 1)));
        conn.deliver(new Message(Map.of("id", 2)));
        conn.deliver(new Message(Map.of("id", 3)));

        assertEquals(3, conn.getQueueSize());
    }

    @Test
    @DisplayName("메시지 전달 순서(FIFO)가 보장되어야 한다")
    void test4_MessageOrderIsFIFO() throws InterruptedException {
        LocalConnection conn = new LocalConnection("c4");
        Message msg1 = new Message(Map.of("seq", 1));
        Message msg2 = new Message(Map.of("seq", 2));

        conn.deliver(msg1);
        conn.deliver(msg2);

        AtomicReference<Message> first = new AtomicReference<>();
        AtomicReference<Message> second = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);

        Thread consumer = new Thread(() -> {
            first.set(conn.poll());
            latch.countDown();
            second.set(conn.poll());
            latch.countDown();
        });
        consumer.start();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, (Integer) first.get().get("seq"));
        assertEquals(2, (Integer) second.get().get("seq"));
    }
}