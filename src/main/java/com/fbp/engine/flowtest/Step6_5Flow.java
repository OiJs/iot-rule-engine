package com.fbp.engine.flowtest;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.FilterNode;
import com.fbp.engine.node.LogNode;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;
import java.util.Map;

public class Step6_5Flow {
    private static final Message POISON_PILL = new Message(java.util.Map.of("poison", true));

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Step 6-5: TimerNode(1초) → LogNode → FilterNode(tick>=3) → PrintNode ===");

        TimerNode timer = new TimerNode("timer", Map.of("intervalMs", 1000L));
        LogNode logger = new LogNode("logger");
        FilterNode filter = new FilterNode("filter", Map.of("key", "tick", "threshold", 3.0));
        PrintNode printer = new PrintNode("printer");

        LocalConnection conn1 = new LocalConnection("timer-to-log");
        LocalConnection conn2 = new LocalConnection("log-to-filter");
        LocalConnection conn3 = new LocalConnection("filter-to-print");

        timer.getOutputPort("out").connect(conn1);
        logger.getOutputPort("out").connect(conn2);
        filter.getOutputPort("out").connect(conn3);

        // LogThread
        Thread logThread = new Thread(() -> {
            while (true) {
                Message msg = conn1.poll();
                if (msg == null || msg == POISON_PILL) {
                    conn2.deliver(POISON_PILL);
                    break;
                }
                logger.process(msg);
            }
            System.out.println("[LogThread] 완료");
        }, "LogThread");

        // FilterThread
        Thread filterThread = new Thread(() -> {
            while (true) {
                Message msg = conn2.poll();
                if (msg == null || msg == POISON_PILL) {
                    conn3.deliver(POISON_PILL);
                    break;
                }
                filter.process(msg);
            }
            System.out.println("[FilterThread] 완료");
        }, "FilterThread");

        // PrintThread
        Thread printThread = new Thread(() -> {
            while (true) {
                Message msg = conn3.poll();
                if (msg == null || msg == POISON_PILL) break;
                printer.process(msg);
            }
            System.out.println("[PrintThread] 완료");
        }, "PrintThread");

        logThread.start();
        filterThread.start();
        printThread.start();
        timer.initialize();

        // 7초 실행
        Thread.sleep(7000);

        timer.shutdown();
        conn1.deliver(POISON_PILL);

        logThread.join();
        filterThread.join();
        printThread.join();

        System.out.println("=== 완료 (LogNode: 모든 tick 출력 / PrintNode: tick>=3만 출력) ===");
    }
}
