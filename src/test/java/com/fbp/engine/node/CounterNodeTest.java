package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CounterNodeTest {

    private CounterNode counter;
    private Connection outputConn;

    @BeforeEach
    void setUp() {
        counter = new CounterNode("counter");
        outputConn = new Connection("out-conn");
        counter.getOutputPort("out").connect(outputConn);
    }

    @Test
    void test1_FirstMessageHasCountOne() throws InterruptedException {
        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(outputConn.poll());
            latch.countDown();
        });
        consumer.start();

        counter.process(new Message(Map.of("data", "item")));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, (Integer) received.get().get("count"));
    }

    @Test
    void test2_CountAccumulates() throws InterruptedException {
        List<Message> collected = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                collected.add(outputConn.poll());
                latch.countDown();
            }
        });
        consumer.start();

        counter.process(new Message(Map.of("data", "a")));
        counter.process(new Message(Map.of("data", "b")));
        counter.process(new Message(Map.of("data", "c")));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, (Integer) collected.get(0).get("count"));
        assertEquals(2, (Integer) collected.get(1).get("count"));
        assertEquals(3, (Integer) collected.get(2).get("count"));
    }

    // 3. 원본 키 유지
    @Test
    void test3_OriginalKeysArePreserved() throws InterruptedException {
        AtomicReference<Message> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            received.set(outputConn.poll());
            latch.countDown();
        });
        consumer.start();

        counter.process(new Message(Map.of("data", "item", "sensor", "A")));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("item", received.get().get("data"));
        assertEquals("A", received.get().get("sensor"));
        assertEquals(1, (Integer) received.get().get("count"));
    }
}