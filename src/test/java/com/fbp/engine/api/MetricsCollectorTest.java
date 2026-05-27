package com.fbp.engine.api;

import com.fbp.engine.metrics.MetricsAggregator;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.metrics.event.NodeProcessEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class MetricsCollectorTest {
    private MetricsCollector collector;
    private MetricsAggregator aggregator;
    private final String FLOW_ID = "flow-1";
    private final String NODE_ID = "node-1";

    @BeforeEach
    void setUp() {
        aggregator = new MetricsAggregator();
        collector = new MetricsCollector(aggregator);
    }

    private void waitForAggregation(int expectedCount) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 2000) {
            var snapshot = collector.getSnapshot(FLOW_ID, NODE_ID);
            if (snapshot != null && snapshot.processedCount() >= expectedCount) {
                return;
            }
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    @Test
    void testRecordProcessingCount() {
        collector.submit(new NodeProcessEvent(System.currentTimeMillis(), FLOW_ID, NODE_ID, true, 100_000, 0, 0));
        
        waitForAggregation(1);
        var snapshot = collector.getSnapshot(FLOW_ID,NODE_ID);
        Assertions.assertEquals(1, snapshot.processedCount());
    }

    @Test
    void testRecordErrorCount() {
        collector.submit(new NodeProcessEvent(System.currentTimeMillis(), FLOW_ID, NODE_ID, false, 100_000, 0, 0));
        
        waitForAggregation(1);
        var snapshot = collector.getSnapshot(FLOW_ID, NODE_ID);
        Assertions.assertEquals(1, snapshot.errorCount());
        Assertions.assertEquals(1, snapshot.processedCount());
    }

    @Test
    void testAverageProcessingTime() {
        collector.submit(new NodeProcessEvent(System.currentTimeMillis(), FLOW_ID, NODE_ID, true, 100_000, 0, 0));
        collector.submit(new NodeProcessEvent(System.currentTimeMillis(), FLOW_ID, NODE_ID, true, 300_000, 0, 0));
        
        waitForAggregation(2);
        var snapshot = collector.getSnapshot(FLOW_ID, NODE_ID);
        // latencyHistogram records in microseconds, so (100us + 300us) / 2 = 200us.
        // snapshot.avgDuration() converts microseconds to milliseconds, so 200us / 1000.0 = 0.2ms.
        Assertions.assertEquals(0.2, snapshot.avgDuration(), 0.01);
    }

    @Test
    void testMultiThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                for (int j = 0; j < iterations; j++) {
                    collector.submit(new NodeProcessEvent(System.currentTimeMillis(), FLOW_ID, NODE_ID, true, 10_000, 0, 0));
                }
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown();

        waitForAggregation(threadCount * iterations);
        var snapshot = collector.getSnapshot(FLOW_ID ,NODE_ID);
        Assertions.assertEquals(threadCount * iterations, snapshot.processedCount());
    }

    @Test
    void testNodeIsolation() {
        collector.submit(new NodeProcessEvent(System.currentTimeMillis(), FLOW_ID, "node-A", true, 100_000, 0, 0));
        collector.submit(new NodeProcessEvent(System.currentTimeMillis(), FLOW_ID, "node-B", true, 200_000, 0, 0));

        waitForAggregation(1); // Need to wait for both, but waitForAggregation is node-specific in my helper.
        // Let's just wait a bit.
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        Assertions.assertEquals(1, collector.getSnapshot(FLOW_ID, "node-A").processedCount());
        Assertions.assertEquals(1, collector.getSnapshot(FLOW_ID, "node-B").processedCount());
        Assertions.assertNotEquals(collector.getSnapshot(FLOW_ID, "node-A").avgDuration(),
                                   collector.getSnapshot(FLOW_ID , "node-B").avgDuration());
    }

    @Test
    void testNonExistentNode() {
        Assertions.assertNull(collector.getSnapshot("unknown-flow", "unknown-node"));
    }
}