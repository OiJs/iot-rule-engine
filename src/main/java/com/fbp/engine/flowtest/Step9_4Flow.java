//package com.fbp.engine.flowtest;
//
//import com.fbp.engine.core.Flow;
//import com.fbp.engine.core.FlowEngine;
//import com.fbp.engine.node.*;
//
//public class Step9_4Flow {
//    public static void main(String[] args) throws InterruptedException {
//        System.out.println("=== 과제 9-4: 온도 모니터링 시스템 시작 ===");
//
//        FlowEngine engine = new FlowEngine();
//
//        TimerNode timer = new TimerNode("timer", 1000);
//
//        TemperatureSensorNode temperatureSensor = new TemperatureSensorNode("sensor", 15.0, 45.0);
//        ThresholdFilterNode filter = new ThresholdFilterNode("filter","temperature", 30.0);
//
//        //FileWriteNode 추가
//        FileWriteNode fileWrite = new FileWriteNode("file", "./log/temperature");
//
//        AlertNode alert = new AlertNode("alert-node");
//        PrintNode logger = new PrintNode("normal-log");
//
//        Flow monitoringFlow = new Flow("temp-monitor")
//                .addNode(timer)
//                .addNode(temperatureSensor)
//                .addNode(filter)
//                .addNode(alert)
//                .addNode(logger)
//                .addNode(fileWrite)
//                .connect("timer", "out", "sensor", "trigger")
//                .connect("sensor", "out", "filter", "in")
//                .connect("filter", "alert", "alert-node", "in")
//                .connect("filter", "normal", "normal-log", "in")
//                .connect("filter", "normal", "file", "in");
//
//        engine.register(monitoringFlow);
//        engine.startFlow("temp-monitor");
//
//        System.out.println(">>> 모니터링 중... (10초 후 종료)");
//
//        Thread.sleep(10000);
//
//        System.out.println("\n>>> 시뮬레이션 종료 시퀀스 가동");
//        engine.shutdown();
//        System.out.println("=== 시스템 종료 완료 ===");
//    }
//}