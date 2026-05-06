package com.fbp.engine.test;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class LoadTester {
    public static PerformanceResult run(InputPort entryPort, Connection sinkConn, int messageCount) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(messageCount);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicLong errors = new AtomicLong(0);

        // [핵심] 마지막 커넥션(sinkConn)에서 메시지를 꺼내서 처리할 전담 일꾼 가동
        Thread sinkWorker = new Thread(() -> {
            while (latch.getCount() > 0) {
                Message msg = sinkConn.poll();
                if (msg != null) {
                    Long startTime = (Long) msg.get("_start_nano");
                    if (startTime != null) {
                        latencies.add(System.nanoTime() - startTime);
                    }
                    latch.countDown();
                } else {
                    Thread.yield(); // 메시지 없으면 잠시 양보
                }
            }
        });
        sinkWorker.start();

        long startTimeMillis = System.currentTimeMillis();

        // 메시지 투입
        for (int i = 0; i < messageCount; i++) {
            Message msg = new Message(new HashMap<>(Map.of("data", i)));
            msg.withEntry("_start_nano", System.nanoTime());
            entryPort.receive(msg);
        }

        // 모든 메시지가 sinkWorker에 의해 처리될 때까지 대기
        boolean completed = latch.await(30, TimeUnit.SECONDS); // 30초 타임아웃
        long endTimeMillis = System.currentTimeMillis();

        if (!completed) {
            System.err.println("경고: 테스트 타임아웃 발생! 처리되지 않은 메시지 수: " + latch.getCount());
        }

        // 통계 계산 (평균 지연 시간 등)
        double throughput = (messageCount - latch.getCount()) / ((endTimeMillis - startTimeMillis) / 1000.0);
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;

        return PerformanceResult.builder()
                .totalMessages(messageCount)
                .throughput(throughput)
                .avgLatency(avgLatency)
                .errorRate(((double) errors.get() / messageCount) * 100)
                .build();
    }
}