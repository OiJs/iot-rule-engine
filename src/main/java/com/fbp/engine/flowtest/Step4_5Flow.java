package com.fbp.engine.flowtest;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.FilterNode;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;
import java.util.HashMap;

//TODO 4-5
public class Step4_5Flow {
    private static final Message POISON_PILL = new Message(new HashMap<>());

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Step 4-5: 3노드 스레드 파이프라인 ===");

        GeneratorNode generator = new GeneratorNode("generator");
        FilterNode filter = new FilterNode("filter", "temperature", 30.0);
        PrintNode printer = new PrintNode("printer");

        Connection conn1 = new Connection("gen-to-filter");
        Connection conn2 = new Connection("filter-to-print");

        generator.getOutputPort("out").connect(conn1);
        filter.getOutputPort("out").connect(conn2);

        Thread generatorThread = new Thread(() -> {
            double[] temps = {25.0, 32.0, 28.0, 35.0, 22.0, 40.0, 18.0, 31.0};
            for (double temp : temps) {
                generator.generate("temperature", temp);
                System.out.println("[GeneratorThread] 생성: " + temp);
                try { Thread.sleep(500); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            conn1.deliver(POISON_PILL);
            System.out.println("[GeneratorThread] 완료");
        }, "GeneratorThread");


        Thread filterThread = new Thread(() -> {
            while (true) {
                Message msg = conn1.poll();
                if (msg == POISON_PILL) {
                    conn2.deliver(POISON_PILL);
                    break;
                }
                Double temp = msg.get("temperature");
                System.out.println("[FilterThread] 필터 처리: " + temp);
                filter.process(msg);
            }
            System.out.println("[FilterThread] 완료");
        }, "FilterThread");

        Thread printerThread = new Thread(() -> {
            while (true) {
                Message msg = conn2.poll();
                if (msg == POISON_PILL) break;
                System.out.println("[PrinterThread] 출력");
                printer.process(msg);
            }
            System.out.println("[PrinterThread] 완료");
        }, "PrinterThread");

        printerThread.start();
        filterThread.start();
        generatorThread.start();

        generatorThread.join();
        filterThread.join();
        printerThread.join();

        System.out.println("=== 완료 ===");
    }
}