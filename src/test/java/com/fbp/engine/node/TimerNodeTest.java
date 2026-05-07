package com.fbp.engine.node;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TimerNodeTest {

    private TimerNode timer;
    private LocalConnection connection;
    private Thread consumerThread;

    @BeforeEach
    void setUp() {
        timer = new TimerNode("timer", 500);
        connection = new LocalConnection("conn");
        timer.getOutputPort("out").connect(connection);
    }

    @AfterEach
    void tearDown() {
        timer.shutdown();
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    // 1. initialize 후 메시지 생성
    @Test
    void test1_InitializeStartsMessageGeneration() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> collected = new ArrayList<>();

        consumerThread = new Thread(() -> {
            Message msg = connection.poll();
            if (msg != null) {
                collected.add(msg);
                latch.countDown();
            }
        });
        consumerThread.start();

        timer.initialize();

        assertTrue(latch.await(2, TimeUnit.SECONDS), "initialize 후 메시지가 생성되어야 함");
        assertFalse(collected.isEmpty());
    }

    // 2. tick 증가
    @Test
    void test2_TickIncrementsSequentially() throws InterruptedException {
        List<Message> collected = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        consumerThread = new Thread(() -> {
            while (collected.size() < 3) {
                Message msg = connection.poll();
                if (msg != null) {
                    collected.add(msg);
                    latch.countDown();
                }
            }
        });
        consumerThread.start();

        timer.initialize();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(0, (Integer) collected.get(0).get("tick"));
        assertEquals(1, (Integer) collected.get(1).get("tick"));
        assertEquals(2, (Integer) collected.get(2).get("tick"));
    }

    // 3. shutdown 후 정지
    @Test
    void test3_ShutdownStopsMessageGeneration() throws InterruptedException {
        List<Message> collected = new ArrayList<>();

        consumerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = connection.poll();
                if (msg != null) collected.add(msg);
            }
        });
        consumerThread.start();

        timer.initialize();
        Thread.sleep(600);
        timer.shutdown();

        int countAfterShutdown = collected.size();
        Thread.sleep(600);

        assertEquals(countAfterShutdown, collected.size(), "shutdown 후 메시지가 추가 생성되지 않아야 함");
    }

    // 4. 주기 확인 — 500ms 주기, 2초간 약 4개
    @Test
    void test4_IntervalProducesExpectedMessageCount() throws InterruptedException {
        List<Message> collected = new ArrayList<>();

        consumerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = connection.poll();
                if (msg != null) collected.add(msg);
            }
        });
        consumerThread.start();

        timer.initialize();
        Thread.sleep(2000);
        timer.shutdown();
        consumerThread.interrupt();

        assertTrue(collected.size() >= 3 && collected.size() <= 5,
                "2초간 약 4개 메시지 생성 예상, 실제: " + collected.size());
    }
}