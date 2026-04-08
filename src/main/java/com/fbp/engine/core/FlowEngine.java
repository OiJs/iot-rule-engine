package com.fbp.engine.core;

import com.fbp.engine.core.Flow.FlowState;
import com.fbp.engine.message.Message;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.ToString;

@ToString
public class FlowEngine {

    public enum State {
        INITIALIZED, RUNNING, STOPPED
    }

    private final Map<String, Flow> flows;
    private State state;
    private final ExecutorService executorService;

    public FlowEngine() {
        this.flows = new HashMap<>();
        this.state = State.INITIALIZED;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    public void register(Flow flow) {
        flows.put(flow.getId(), flow);
        System.out.println("[Engine] 플로우 '" + flow.getId() + "' 등록됨");
    }

    public void startFlow(String flowId){
        Flow flow = flows.get(flowId);
        if (flow == null) {
            throw new IllegalArgumentException("존재하지 않는 플로우 ID: " + flowId);
        }

        List<String> errors = flow.validate();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("플로우 검증 실패: " + errors);
        }
        flow.initialize();

        for(Connection conn : flow.getConnections()) {
            executorService.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {

                    Message msg = conn.poll();
                    if(msg != null && conn.getTarget() != null) {
                        conn.getTarget().receive(msg);
                    }
                }
            });
        }

        flow.setFlowState(FlowState.RUNNING);
        this.state = State.RUNNING;

        System.out.println("[Engine] 플로우 '" + flowId + "' 시작됨");
    }

    public void stopFlow(String flowId) {
        Flow flow = flows.get(flowId);
        if (flow != null) {
            flow.shutdown();
            flow.setFlowState(FlowState.STOPPED);
            System.out.println("[Engine] 플로우 '" + flowId + "' 정지됨");
        }
    }

    public void shutdown() {
        for(Flow flow : flows.values()) {
            flow.shutdown();
        }
        this.state = State.STOPPED;
        executorService.shutdown();
    }

    public State getState() {
        return state;
    }

    public Map<String, Flow> getFlows() {
        return flows;
    }

    public List<Flow> listFlows() {
        return flows.values().stream().toList();
    }

    public void startEngineCLI() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== FBP Engine CLI 가동 ===");

        while (state != State.STOPPED) {
            System.out.print("fbp> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] parts = input.split("\\s+");
            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "list":
                        printFlowList();
                        break;

                    case "start":
                        if (parts.length < 2) throw new IllegalArgumentException("ID를 입력하세요.");
                        startFlow(parts[1]);
                        break;

                    case "stop":
                        if (parts.length < 2) throw new IllegalArgumentException("ID를 입력하세요.");
                        stopFlow(parts[1]);
                        break;

                    case "exit":
                        shutdown();
                        break;

                    case "help":
                        System.out.println("명령어: list, start <id>, stop <id>, exit, help");
                        break;

                    default:
                        System.out.println("알 수 없는 명령어입니다: " + command);
                }
            } catch (Exception e) {
                System.out.println("[Error] " + e.getMessage());
            }
        }
        scanner.close();
    }

    private void printFlowList() {
        int index = 1;
        List<Flow> flowList = listFlows();
        if (flowList.isEmpty()) {
            System.out.println("등록된 플로우가 없습니다.");
            return;
        }
        for (Flow flow : flowList) {
            System.out.printf("[%d] %-12s %s\n", index++, flow.getId(), flow.getFlowState());
        }
    }
}
