package com.fbp.engine;

import com.fbp.engine.core.FlowEngine;
import java.util.Scanner;

public class FlowEngineCLI {
    public static void main(String[] args) {
        FlowEngine engine = new FlowEngine();

        setupSampleFlows(engine);

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("=== FBP Engine CLI ===");

        while(running) {
            System.out.print("fbp> ");
            String input = scanner.nextLine().trim();
            String[] parts = input.split("\\s+");

            String command = parts[0].toLowerCase();

             try {
                 switch (command) {
                     case "list":
                         printFlowList(engine);
                        break;

                     case "start":
                         if(parts.length < 2) throw new IllegalArgumentException();
                         engine.startFlow(parts[1]);
                         break;

                     case "stop":
                         if(parts.length < 2) throw new IllegalArgumentException();
                         engine.stopFlow(parts[1]);

                     case "exit":
                         System.out.println("[Engine] 종료됨");
                         running = false;
                         break;

                     case"help":
                         System.out.println();

                     default:
                         System.out.println("알 수 없는 명령어입니다: " + command);
                 }

             } catch (Exception e) {
                 System.out.println(e.getMessage());
             }
        }
    }

    private static void setupSampleFlows(FlowEngine engine) {
    }

    private static void printFlowList(FlowEngine engine) {
    }
}
