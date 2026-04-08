package com.fbp.engine.flowtest;

import com.fbp.engine.core.Connection;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;

public class Step3_7Flow {
    public static void main(String[] args) {
        System.out.println("=== Step 3-7: GeneratorNode → Connection → PrintNode ===");

        GeneratorNode generator = new GeneratorNode("gen-0");
        PrintNode printNode = new PrintNode("T");
        Connection conn = new Connection("conn-0");

        conn.setTarget(printNode.getInputPort("in"));
        generator.getOutputPort("out").connect(conn);

        generator.generate("temperature", 25.5);
    }
}