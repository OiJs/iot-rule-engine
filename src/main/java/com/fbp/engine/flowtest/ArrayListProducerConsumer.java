package com.fbp.engine.flowtest;

import java.util.ArrayList;
import java.util.List;

//TODO 4-1
public class ArrayListProducerConsumer {
    public static void main(String[] args) throws InterruptedException {
        List<String> buffer = new ArrayList<>();

        Thread producer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                buffer.add("메시지-" + i);
                try { Thread.sleep(10); } 
                catch (InterruptedException e) { break; }
            }
            System.out.println("[생산자] 완료");
        });

        Thread consumer = new Thread(() -> {
            int received = 0;
            while (received < 100) {
                if (!buffer.isEmpty()) {
                    String msg = buffer.remove(0);
                    System.out.println("[소비자] " + msg);
                    received++;
                }
            }
            System.out.println("[소비자] 완료");
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}