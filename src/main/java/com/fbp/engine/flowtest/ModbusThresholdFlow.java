//package com.fbp.engine.flowtest;
//
//import com.fbp.engine.core.Flow;
//import com.fbp.engine.core.FlowEngine;
//import com.fbp.engine.message.Message;
//import com.fbp.engine.node.ThresholdFilterNode;
//import com.fbp.engine.node.TimerNode;
//import com.fbp.engine.node.TransformNode;
//import com.fbp.engine.node.modbus.ModbusReaderNode;
//import com.fbp.engine.node.modbus.ModbusWriterNode;
//import com.fbp.engine.protocol.ModbusTcpSimulator;
//import java.util.Map;
//
//public class ModbusThresholdFlow {
//    public static void main(String[] args) throws InterruptedException {
//        int port = 5020;
//
//        ModbusTcpSimulator simulator = new ModbusTcpSimulator(port, 10);
//        simulator.setRegister(0, 250);
//        simulator.setRegister(2, 0);
//        simulator.start();
//
//        FlowEngine engine = new FlowEngine();
//        TimerNode timer = new TimerNode("timer", 1000);
//
//        ModbusReaderNode reader = new ModbusReaderNode("reader", Map.of(
//                "host", "localhost",
//                "port", port,
//                "slaveId", 1,
//                "startAddress", 0,
//                "count", 2,
//                "registerMapping", Map.of("temperature", 0)
//        ));
//
//        TransformNode transformer = new TransformNode("transformer", (msg) -> {
//            return new Message(Map.of("temperature", 1));
//        });
//
//        ModbusWriterNode writer = new ModbusWriterNode("writer", Map.of(
//                "host", "localhost",
//                "port", port,
//                "registerAddress", 2,
//                "valueField", "temperature",
//                "scale", 1.0
//        ));
//
//        ThresholdFilterNode filter = new ThresholdFilterNode("filter", "temperature", 300.0);
//
//        Flow flow = new Flow("control-flow")
//                .addNode(timer)
//                .addNode(reader)
//                .addNode(filter)
//                .addNode(transformer) // 노드 추가
//                .addNode(writer)
//                .connect("timer", "out", "reader", "trigger")
//                .connect("reader", "out", "filter", "in")
//                .connect("filter", "alert", "transformer", "in") // 필터 -> 트랜스포머
//                .connect("transformer", "out", "writer", "in"); // 트랜스포머 -> 라이터
//
//        engine.register(flow);
//        engine.startFlow("control-flow");
//
//        System.out.println("현재 주소 2의 값: " + simulator.getRegister(2));
//        System.out.println("온도를 35.0도로 상승시킵니다...");
//        simulator.setRegister(0, 350);
//
//        Thread.sleep(2500);
//
//        int controlValue = simulator.getRegister(2);
//        System.out.println("최종 주소 2의 값: " + controlValue);
//
//        if (controlValue == 1) {
//            System.out.println("검증 성공: TransformNode를 통해 알림 코드 1이 기록되었습니다.");
//        } else {
//            System.out.println("검증 실패: 값이 변경되지 않았습니다.");
//        }
//    }
//}