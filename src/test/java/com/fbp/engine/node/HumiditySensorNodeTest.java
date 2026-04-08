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

import static org.junit.jupiter.api.Assertions.*;

class HumiditySensorNodeTest {

    private HumiditySensorNode sensor;
    private Connection outputConn;

    @BeforeEach
    void setUp() {
        sensor = new HumiditySensorNode("humidity-1", 30.0, 90.0);
        outputConn = new Connection("out-conn");
        sensor.getOutputPort("out").connect(outputConn);
    }

    @Test
    void test1_HumidityIsWithinRange() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> collected = new ArrayList<>();

        Thread consumer = new Thread(() -> {
            collected.add(outputConn.poll());
            latch.countDown();
        });
        consumer.start();

        sensor.process(new Message(Map.of()));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        double humidity = ((Number) collected.get(0).get("humidity")).doubleValue();
        assertTrue(humidity >= 30.0 && humidity <= 90.0,
                "습도가 30~90 범위를 벗어남: " + humidity);
    }

    @Test
    void test2_RequiredKeysExist() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> collected = new ArrayList<>();

        Thread consumer = new Thread(() -> {
            collected.add(outputConn.poll());
            latch.countDown();
        });
        consumer.start();

        sensor.process(new Message(Map.of()));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        Message msg = collected.get(0);
        assertTrue(msg.hasKey("sensorId"));
        assertTrue(msg.hasKey("humidity"));
        assertTrue(msg.hasKey("unit"));
    }

    @Test
    void test3_SensorIdMatchesNodeId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> collected = new ArrayList<>();

        Thread consumer = new Thread(() -> {
            collected.add(outputConn.poll());
            latch.countDown();
        });
        consumer.start();

        sensor.process(new Message(Map.of()));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("humidity-1", collected.get(0).get("sensorId"));
    }

    @Test
    void test4_EachTriggerProducesOneMessage() throws InterruptedException {
        List<Message> collected = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                collected.add(outputConn.poll());
                latch.countDown();
            }
        });
        consumer.start();

        sensor.process(new Message(Map.of()));
        sensor.process(new Message(Map.of()));
        sensor.process(new Message(Map.of()));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(3, collected.size());
    }
}