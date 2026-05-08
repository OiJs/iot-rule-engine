//package com.fbp.engine.flowtest;
//
//import com.fbp.engine.core.LocalConnection;
//import com.fbp.engine.node.FilterNode;
//import com.fbp.engine.node.GeneratorNode;
//import com.fbp.engine.node.PrintNode;
//
//public class Step3_10Flow {
//    public static void main(String[] args) {
//        System.out.println("=== Step 3-10: Generator → Filter(30) → Print ===");
//
//        GeneratorNode generator = new GeneratorNode("gen-c");
//        FilterNode filter = new FilterNode("filterA", "temperature", 30.0);
//        PrintNode printer = new PrintNode("printer-C");
//
//        LocalConnection conn3 = new LocalConnection("conn-3");
//        conn3.setTarget(filter.getInputPort("in"));
//        generator.getOutputPort("out").connect(conn3);
//
//        LocalConnection conn4 = new LocalConnection("conn-4");
//        conn4.setTarget(printer.getInputPort("in"));
//        filter.getOutputPort("in").connect(conn4);
//
//        System.out.println("--- Test 1: 25.0 (미달, 출력 없어야 함) ---");
//        generator.generate("temperature", 25.0);
//
//        System.out.println("--- Test 2: 35.0 (초과, 출력 있어야 함) ---");
//        generator.generate("temperature", 35.0);
//    }
//}