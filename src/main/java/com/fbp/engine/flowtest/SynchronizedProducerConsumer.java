package com.fbp.engine.flowtest;

import java.util.ArrayList;
import java.util.List;

//TODO 4-2
public class SynchronizedProducerConsumer {
    public static void main(String[] args) throws InterruptedException {
        List<String> buffer = new ArrayList<>();
        final String END = "END";

        Thread producer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                synchronized (buffer) {
                    buffer.add("메시지-" + i);
                    buffer.notifyAll();
                }
                try { Thread.sleep(10); }
                catch (InterruptedException e) { break; }
            }

            synchronized (buffer) {
                buffer.add(END);
                buffer.notify();
            }
            System.out.println("[생산자] 완료");
        });

        Thread consumer = new Thread(() -> {
            while (true) {
                String msg;
                synchronized (buffer) {
                    while (buffer.isEmpty()) {
                        try { buffer.wait(); }
                        catch (InterruptedException e) { return; }
                    }
                    msg = buffer.remove(0);
                }
                if (END.equals(msg)) {
                    break;
                }
                System.out.println("[소비자] " + msg);
            }
            System.out.println("[소비자] 완료");
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}