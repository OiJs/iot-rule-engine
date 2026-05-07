package com.fbp.engine.flowtest;

import com.fbp.engine.core.LocalConnection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;
import java.util.HashMap;

public class Step4_6Flow {
    private static final Message POISON_PILL = new Message(new HashMap<>());

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("Case 1: 생산(0.1초) 빠름 > 소비(1초) 느림");
        System.out.println("========================================");
        runTest(100, 1000);

        System.out.println("\n========================================");
        System.out.println("Case 2: 생산(1초) 느림 < 소비(0.1초) 빠름");
        System.out.println("========================================");
        runTest(1000, 100);
    }

    private static void runTest(long produceDelay, long consumeDelay) throws InterruptedException {
        GeneratorNode generator = new GeneratorNode("generator");
        PrintNode printer = new PrintNode("printer");
        LocalConnection connection = new LocalConnection("conn", 100);

        generator.getOutputPort("out").connect(connection);

        // 생산자
        Thread producerThread = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                generator.generate("temperature", 20.0 + i);
                System.out.println("[생산자] 생성: " + (20.0 + i)
                        + " | 버퍼: " + connection.getQueueSize());
                try { Thread.sleep(produceDelay); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            connection.deliver(POISON_PILL);
            System.out.println("[생산자] 완료");
        }, "ProducerThread");

        // 소비자
        Thread consumerThread = new Thread(() -> {
            while (true) {
                System.out.println("[소비자] 대기 중... | 버퍼: " + connection.getQueueSize());
                Message msg = connection.poll();
                if (msg == POISON_PILL) break;
                printer.process(msg);
                try { Thread.sleep(consumeDelay); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[소비자] 완료");
        }, "ConsumerThread");

        consumerThread.start();
        producerThread.start();

        producerThread.join();
        consumerThread.join();
    }
}