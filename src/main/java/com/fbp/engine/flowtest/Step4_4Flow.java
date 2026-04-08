package com.fbp.engine.flowtest;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;

//TODO 4-4
public class Step4_4Flow {
    public static void main(String[] args) throws InterruptedException {

        GeneratorNode generatorNode = new GeneratorNode("gen");
        PrintNode printNode = new PrintNode("1");
        Connection connection = new Connection("conn1");

        Thread producerThread = new Thread(() -> {
            System.out.println("[생산자 스레드] 시작");

            for (int i = 0; i < 5; i++) {
                generatorNode.generate("temperature", 20.0 + i);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[생산자 스레드] 종료");
        }, "ProducerThread");

        Thread consumerThread = new Thread(() -> {
            System.out.println("[소비자 스레드] 시작");
            for (int i = 0; i < 5; i++) {
                Message msg = connection.poll();
                if (msg != null) {
                    printNode.process(msg);
                }
            }
            System.out.println("[소비자 스레드] 완료");
        }, "ConsumerThread");

        generatorNode.getOutputPort("out").connect(connection);

        consumerThread.start();
        producerThread.start();

        producerThread.join();
        consumerThread.join();

        System.out.println("메인 스레드 종료");
    }
}
