package com.fbp.engine.flowtest;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

//TODO 4-3
public class BlockingQueueProducerConsumer {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(200);
        final String END = "END";

        Thread producer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                try {
                    queue.put("메시지-" + i);
                    Thread.sleep(10);
                } catch (InterruptedException e) { break; }
            }
            try { queue.put(END); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println("[생산자] 완료");
        });

        Thread consumer = new Thread(() -> {
            while (true) {
                try {
                    String msg = queue.take();
                    if (END.equals(msg)) break;
                    System.out.println("[소비자] " + msg);
                } catch (InterruptedException e) { break; }
            }
            System.out.println("[소비자] 완료");
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}