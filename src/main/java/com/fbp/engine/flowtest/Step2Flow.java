package com.fbp.engine.flowtest;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.PrintNode;
import java.util.HashMap;
import java.util.Map;

public class Step2Flow {
    public static void main(String[] args) {
        System.out.println("=== Step 2: PrintNode & Message Test ===");

        Map<String, Object> data = new HashMap<>();
        data.put("temperature", 25.5);
        data.put("unit", "°C");
        data.put("location", "Room 101");

        Message message = new Message(data);
        PrintNode printer = new PrintNode("printer-1");

        printer.process(message);

        System.out.println("\n--- Immutability Test (withEntry) ---");
        Message updatedMessage = message.withEntry("status", "NORMAL");

        System.out.print("Original: ");
        printer.process(message);

        System.out.print("Updated:  ");
        printer.process(updatedMessage);
    }
}