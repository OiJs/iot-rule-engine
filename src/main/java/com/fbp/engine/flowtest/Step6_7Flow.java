package com.fbp.engine.flowtest;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.DelayNode;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;

import java.util.Map;

public class Step6_7Flow {
    private static final Message POISON_PILL = new Message(Map.of());

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Step 6-7: DelayNode(1초 지연) ===");

        GeneratorNode generator = new GeneratorNode("generator");
        DelayNode delay = new DelayNode("delay", 1000);
        PrintNode printer = new PrintNode("printer");

        Connection conn1 = new Connection("gen-to-delay");
        Connection conn2 = new Connection("delay-to-print");

        generator.getOutputPort("out").connect(conn1);
        delay.getOutputPort("out").connect(conn2);

        Thread delayThread = new Thread(() -> {
            while (true) {
                Message msg = conn1.poll();
                if (msg == POISON_PILL) {
                    conn2.deliver(POISON_PILL);
                    break;
                }
                delay.process(msg);
            }
        }, "DelayThread");

        Thread printThread = new Thread(() -> {
            while (true) {
                Message msg = conn2.poll();
                if (msg == POISON_PILL) break;
                printer.process(msg);
            }
        }, "PrintThread");

        delayThread.start();
        printThread.start();

        for (int i = 1; i <= 3; i++) {
            System.out.println("[Generator] 생성: item-" + i);
            generator.generate("data", "item-" + i);
        }

        conn1.deliver(POISON_PILL);
        delayThread.join();
        printThread.join();

        System.out.println("=== 완료 ===");
    }
}