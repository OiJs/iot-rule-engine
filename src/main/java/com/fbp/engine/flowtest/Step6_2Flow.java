package com.fbp.engine.flowtest;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TransformNode;

public class Step6_2Flow {
    public static void main(String[] args) {
        GeneratorNode generator = new GeneratorNode("gen");
        TransformNode f2c = new TransformNode("f2c", msg -> {
            Double fahrenheit = msg.get("temperature");
            double celsius = (fahrenheit - 32) * 5.0 / 9.0;
            return msg.withEntry("temperature", Math.round(celsius * 9.0));
        });

        PrintNode printer = new PrintNode("A");

        LocalConnection conn1 = new LocalConnection("conn1");
        LocalConnection conn2 = new LocalConnection("conn2");

        generator.getOutputPort("out").connect(conn1);
        f2c.getOutputPort("out").connect(conn2);

        Thread transformThread = new Thread(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                Message message = conn1.poll();
                if(message != null) {
                    f2c.process(message);
                }
            }
        }, "TransformThread");

        Thread printThread = new Thread(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                Message message = conn2.poll();
                if(message != null) {
                    printer.process(message);
                }
            }
        }, "PrintThread");

        transformThread.start();
        printThread.start();

        double[] fahrenheits = {32.0, 98.6, 212.0, 72.0, 100.0};
        for (double f : fahrenheits) {
            generator.generate("temperature", f);
            System.out.println("[Generator] 화씨: " + f);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        transformThread.interrupt();
        printThread.interrupt();

        System.out.println("=== 완료 ===");
    }

}
