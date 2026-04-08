package com.fbp.engine.flowtest;

import com.fbp.engine.core.Connection;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;

public class Step3_8Test {
    public static void main(String[] args) {
        System.out.println("=== Step 3-8: 1:N Fan-out Test ===");

        GeneratorNode generator = new GeneratorNode("gen-1");
        PrintNode printNodeA = new PrintNode("A");
        PrintNode printNodeB = new PrintNode("B");

        Connection conn1 = new Connection("conn-1");
        conn1.setTarget(printNodeA.getInputPort("in"));

        Connection conn2 = new Connection("conn-2");
        conn2.setTarget(printNodeB.getInputPort("in"));

        generator.getOutputPort("out").connect(conn1);
        generator.getOutputPort("out").connect(conn2);

        generator.generate("alert", "Fire detected!");
    }
}