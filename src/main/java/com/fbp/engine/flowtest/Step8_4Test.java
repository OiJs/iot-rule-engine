package com.fbp.engine.flowtest;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;
import java.util.List;
import java.util.Map;

public class Step8_4Test {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 과제 8-4: Flow 상태 개별 관리 테스트 ===");

        FlowEngine engine = new FlowEngine();

        TimerNode timerNodeA = new TimerNode("timerA", Map.of("intervalMs", 500L));
        PrintNode printNodeA = new PrintNode("printA");
        Flow flowA = new Flow("flowA")
                .addNode(timerNodeA)
                .addNode(printNodeA)
                .connect("timerA", "out", "printA", "in");

        TimerNode timerNodeB = new TimerNode("timerB", Map.of("intervalMs", 1000L));
        PrintNode printNodeB = new PrintNode("printB");
        Flow flowB = new Flow("flowB")
                .addNode(timerNodeB)
                .addNode(printNodeB)
                .connect("timerB", "out", "printB", "in");

        engine.register(flowA);
        engine.register(flowB);

        engine.startFlow("flowA");
        engine.startFlow("flowB");

        System.out.println(">>> 두 플로우가 독립적으로 실행 중입니다. (5초간 관찰)");

        Thread.sleep(5000);

        System.out.println(">>> flowA stop");
        engine.stopFlow("flowA");
        List<Flow> flows = engine.listFlows();

        System.out.println(flows.toString());
        engine.shutdown();

        System.out.println("=== FlowEngine 테스트 완료 ===");
    }
}
