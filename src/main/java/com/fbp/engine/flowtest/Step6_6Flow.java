package com.fbp.engine.flowtest;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.CounterNode;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;

import java.util.Map;

public class Step6_6Flow {
    private static final Message POISON_PILL = new Message(Map.of());

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Step 6-6: CounterNode ===");

        GeneratorNode generator = new GeneratorNode("generator");
        CounterNode counter = new CounterNode("counter");
        PrintNode printer = new PrintNode("printer");

        Connection conn1 = new Connection("gen-to-counter");
        Connection conn2 = new Connection("counter-to-print");

        generator.getOutputPort("out").connect(conn1);
        counter.getOutputPort("out").connect(conn2);

        Thread counterThread = new Thread(() -> {
            while (true) {
                Message msg = conn1.poll();
                if (msg == POISON_PILL) {
                    conn2.deliver(POISON_PILL);
                    break;
                }
                counter.process(msg);
            }
        }, "CounterThread");

        Thread printThread = new Thread(() -> {
            while (true) {
                Message msg = conn2.poll();
                if (msg == POISON_PILL) break;
                printer.process(msg);
            }
        }, "PrintThread");

        counterThread.start();
        printThread.start();

        for (int i = 1; i <= 5; i++) {
            generator.generate("data", "item-" + i);
            Thread.sleep(300);
        }

        conn1.deliver(POISON_PILL);
        counterThread.join();
        printThread.join();

        counter.shutdown();
    }
}