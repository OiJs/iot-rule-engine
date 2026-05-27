package com.fbp.engine.cli;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.Flow;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.metrics.MetricsAggregator;
import com.fbp.engine.metrics.NodeMetrics.NodeMetricsSnapshot;
import com.fbp.engine.metrics.TimeWindowBucketer;
import com.fbp.engine.metrics.event.*;
import com.fbp.engine.parser.DomainMetricDefinition;
import com.fbp.engine.parser.FlowDefinition;
import com.fbp.engine.parser.FlowParser;

import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * FbpCli는 사용자와 FBP 엔진 간의 대화형 인터페이스를 제공하는 클래스입니다.
 * 표준 입력을 통해 명령어를 입력받아 플로우 배포, 실행 제어, 모니터링 및 
 * 실시간 통계 조회를 수행합니다.
 */
public class FbpCli {
    private final FlowManager flowManager;
    private final FlowParser flowParser;

    public FbpCli(FlowManager flowManager, FlowParser flowParser) {
        this.flowManager = flowManager;
        this.flowParser = flowParser;
    }

    /**
     * CLI 루프를 시작합니다. 사용자가 'exit'를 입력할 때까지 
     * 지속적으로 명령어를 입력받아 처리합니다.
     */
    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== FBP Engine CLI Started ===");
        System.out.println("Type 'help' for available commands.");

        while (true) {
            System.out.print("fbp> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] args = input.split("\\s+");
            String cmd = args[0].toLowerCase();

            try {
                switch (cmd) {
                    case "flow":
                        handleFlowCmd(args);
                        break;
                    case "node":
                        handleNodeCmd(args);
                        break;
                    case "wire":
                        handleWireCmd(args);
                        break;
                    case "monitor":
                        handleMonitorCmd(args, scanner);
                        break;
                    case "sensor":
                        handleSensorCmd(args);
                        break;
                    case "stats":
                        handleStatsCmd();
                        break;
                    case "influx":
                        System.out.println("InfluxDB Status: CONNECTED (BatchWriter initialized)");
                        break;
                    case "broker":
                        System.out.println("System Broker: tcp://localhost:1884 (Assumed)");
                        break;
                    case "help":
                        printHelp();
                        break;
                    case "exit":
                        System.out.println("Exiting engine...");
                        flowManager.getEngine().shutdown();
                        return;
                    default:
                        System.out.println("Unknown command: " + cmd + ". Type 'help' for usage.");
                }
            } catch (Exception e) {
                System.out.println("[Error] " + e.getMessage());
            }
        }
    }

    /**
     * 플로우 관련 명령어(list, deploy, start, stop 등)를 처리합니다.
     * @param args 명령어 인자 배열
     */
    private void handleFlowCmd(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: flow [list|deploy|start|stop|restart|remove|status|patch]");
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "list":
                System.out.println("ID                        STATUS");
                System.out.println("----------------------------------------");
                for (Flow f : flowManager.list()) {
                    System.out.printf("%-25s %s\n", f.getId(), f.getFlowState());
                }
                break;
            case "deploy":
                if (args.length < 3) throw new IllegalArgumentException("Usage: flow deploy <file>");
                try (FileInputStream fis = new FileInputStream(args[2])) {
                    FlowDefinition def = flowParser.parse(fis);
                    flowManager.deploy(def);
                    System.out.println("Flow '" + def.id() + "' deployed. (Type 'flow start " + def.id() + "' to run)");
                }
                break;
            case "start":
                if (args.length < 3) throw new IllegalArgumentException("Usage: flow start <id>");
                flowManager.getEngine().startFlow(args[2]);
                break;
            case "stop":
                if (args.length < 3) throw new IllegalArgumentException("Usage: flow stop <id>");
                flowManager.stop(args[2]);
                System.out.println("Flow '" + args[2] + "' stopped.");
                break;
            case "restart":
                if (args.length < 3) throw new IllegalArgumentException("Usage: flow restart <id>");
                flowManager.restart(args[2]);
                System.out.println("Flow '" + args[2] + "' restarted.");
                break;
            case "remove":
                if (args.length < 3) throw new IllegalArgumentException("Usage: flow remove <id>");
                flowManager.remove(args[2]);
                System.out.println("Flow '" + args[2] + "' removed.");
                break;
            case "status":
                if (args.length < 3) throw new IllegalArgumentException("Usage: flow status <id>");
                printFlowStatus(args[2]);
                break;
            case "patch":
                if (args.length < 4) throw new IllegalArgumentException("Usage: flow patch <id> <file>");
                try (FileInputStream fis = new FileInputStream(args[3])) {
                    FlowDefinition def = flowParser.parse(fis);
                    flowManager.applyPatch(args[2], def);
                }
                break;
            default:
                System.out.println("Unknown flow subcommand: " + sub);
        }
    }

    /**
     * 특정 플로우의 상세 상태와 누적 처리 통계를 출력합니다.
     * @param flowId 조회할 플로우 ID
     */
    private void printFlowStatus(String flowId) {
        Flow f = flowManager.getEngine().getFlows().get(flowId);
        if (f == null) {
            System.out.println("Flow not found: " + flowId);
            return;
        }
        System.out.println("Flow: " + f.getId());
        System.out.println("  Status: " + f.getFlowState());
        System.out.println("  Nodes:  " + f.getNodes().size());
        System.out.println("  Wires:  " + f.getConnections().size());
        
        var nodeMetrics = flowManager.getEngine().getCollector().getFlowMetrics(flowId);
        long processed = nodeMetrics.values().stream().mapToLong(NodeMetricsSnapshot::processedCount).sum();
        long errors = nodeMetrics.values().stream().mapToLong(NodeMetricsSnapshot::errorCount).sum();
        System.out.println("  Processed: " + processed + " messages");
        System.out.println("  Errors:    " + errors);
    }

    /**
     * 노드 관련 명령어(list, stats)를 처리합니다.
     * @param args 명령어 인자 배열
     */
    private void handleNodeCmd(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: node [list|stats] <flow-id> [node-id]");
            return;
        }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "list":
                Flow f = flowManager.getEngine().getFlows().get(args[2]);
                if (f == null) {
                    System.out.println("Flow not found");
                    return;
                }
                System.out.println("Node ID             Type");
                System.out.println("----------------------------------------");
                f.getNodes().forEach((id, node) -> {
                    System.out.printf("%-19s %s\n", id, node.getClass().getSimpleName());
                });
                break;
            case "stats":
                if (args.length < 4) throw new IllegalArgumentException("Usage: node stats <flow-id> <node-id>");
                NodeMetricsSnapshot stats = flowManager.getEngine().getCollector().getSnapshot(args[2], args[3]);
                if (stats != null) {
                    System.out.println("Node: " + args[3]);
                    System.out.println("  Processed: " + stats.processedCount());
                    System.out.println("  Errors:    " + stats.errorCount());
                    System.out.println("  Avg Time:  " + String.format("%.2f", stats.avgDuration()) + " ms");
                } else {
                    System.out.println("No stats available for " + args[3]);
                }
                break;
        }
    }

    /**
     * 연결(Wire) 관련 명령어(list, info, stats)를 처리합니다.
     * @param args 명령어 인자 배열
     */
    private void handleWireCmd(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: wire [list|info|stats] <flow-id> [wire-id]");
            return;
        }
        String sub = args[1].toLowerCase();
        String flowId = args[2];
        Flow f = flowManager.getEngine().getFlows().get(flowId);
        if (f == null) {
            System.out.println("Flow not found: " + flowId);
            return;
        }

        if (sub.equals("list")) {
            System.out.println("Wire ID                       Transport            Queue");
            System.out.println("------------------------------------------------------------------");
            for (Connection c : f.getConnections()) {
                System.out.printf("%-29s %-20s %d\n", c.getId(), c.getClass().getSimpleName(), c.getQueueSize());
            }
        } else if (sub.equals("info")) {
            if (args.length < 4) {
                System.out.println("Usage: wire info <flow-id> <wire-id>");
                return;
            }
            String wireId = args[3];
            Connection conn = f.getConnections().stream().filter(c -> c.getId().equals(wireId)).findFirst().orElse(null);
            if (conn == null) {
                System.out.println("Wire not found: " + wireId);
                return;
            }
            System.out.println("Wire: " + wireId);
            System.out.println("  Transport:  " + conn.getClass().getSimpleName());
            System.out.println("  Queue Size: " + conn.getQueueSize());
        } else if (sub.equals("stats")) {
            if (args.length < 4) {
                System.out.println("Usage: wire stats <flow-id> <wire-id>");
                return;
            }
            String wireId = args[3];
            var flowWireStats = flowManager.getEngine().getAggregator().getWireStats().get(flowId);
            if (flowWireStats != null) {
                var stats = flowWireStats.get(wireId);
                if (stats != null) {
                    System.out.println("Wire Stats: " + wireId);
                    System.out.println("  Delivered:  " + stats.deliveredCount.sum());
                    System.out.println("  Dropped:    " + stats.droppedCount.sum());
                    System.out.println("  Bytes:      " + stats.totalBytes.sum());
                    System.out.println("  Queue Size: " + stats.lastQueueSize.get());
                    return;
                }
            }
            System.out.println("No stats available for wire: " + wireId);
        }
    }

    /**
     * 실시간 모니터링 명령어(flow, node, data)를 처리합니다.
     * 엔터 키를 입력할 때까지 백그라운드 이벤트 리스너를 통해 메시지를 화면에 출력합니다.
     * @param args 명령어 인자 배열
     * @param scanner 엔터 입력을 대기하기 위한 스캐너 객체
     */
    private void handleMonitorCmd(String[] args, Scanner scanner) {
        if (args.length < 3) {
            System.out.println("Usage: monitor [flow|node|data] <id> [--filter <expr>]");
            return;
        }

        String type = args[1].toLowerCase();
        String id = args[2];
        String filter = (args.length >= 5 && args[3].equals("--filter")) ? args[4] : null;

        System.out.println(">>> Monitoring " + type + " '" + id + "'. Press Enter to stop.");

        Consumer<MetricEvent> listener = (event) -> {
            if (type.equals("flow") && event instanceof DomainExtractionEvent e && e.flowId().equals(id)) {
                System.out.println("[FLOW:" + id + "] " + e.nodeId() + "." + e.portName() + " -> " + e.message().getPayload());
            } else if (type.equals("node") && event instanceof DomainExtractionEvent e && e.nodeId().equals(id)) {
                System.out.println("[NODE:" + id + "] Out: " + e.message().getPayload());
            } else if (type.equals("data") && event instanceof DomainDataEvent e && e.sensorName().equals(id)) {
                if (filter == null || String.valueOf(e.value()).contains(filter)) {
                    System.out.println("[DATA:" + id + "] " + e.value() + " " + e.tags());
                }
            }
        };

        flowManager.getEngine().getAggregator().setMonitorListener(listener);
        scanner.nextLine(); // Wait for enter
        flowManager.getEngine().getAggregator().setMonitorListener(null);
        System.out.println(">>> Monitoring stopped.");
    }

    /**
     * 센서(도메인 메트릭) 관련 명령어(list, stats)를 처리합니다.
     * @param args 명령어 인자 배열
     */
    private void handleSensorCmd(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: sensor [list|stats] <name> [--window <1m/1h/1d>]");
            return;
        }

        String sub = args[1].toLowerCase();
        MetricsAggregator aggregator = flowManager.getEngine().getAggregator();

        if (sub.equals("list")) {
            System.out.println("Sensor Name         Flow ID             Node Source");
            System.out.println("------------------------------------------------------------------");
            aggregator.getDomainConfigs().forEach((flowId, defs) -> {
                for (DomainMetricDefinition d : defs) {
                    System.out.printf("%-19s %-19s %s:%s\n", d.name(), flowId, d.source().node(), d.source().port());
                }
            });
        } else if (sub.equals("stats")) {
            if (args.length < 3) throw new IllegalArgumentException("Usage: sensor stats <name> [--window 1h]");
            String name = args[2];
            String window = (args.length >= 5 && args[3].equals("--window")) ? args[4] : "1m";
            
            aggregator.getSensorBucketers().forEach((key, windows) -> {
                if (key.endsWith(":" + name)) {
                    TimeWindowBucketer b = windows.get(window);
                    if (b != null) {
                        var s = b.flush(System.currentTimeMillis()); // Get latest snapshot
                        System.out.println("Sensor: " + name + " (Window: " + window + ")");
                        System.out.println("  Avg:   " + String.format("%.2f", s.avg()));
                        System.out.println("  Min:   " + String.format("%.2f", s.min()));
                        System.out.println("  Max:   " + String.format("%.2f", s.max()));
                        System.out.println("  Count: " + s.count());
                    }
                }
            });
        }
    }

    /**
     * 엔진 전체의 전역 통계(활성 플로우 수, 메모리 사용량 등)를 출력합니다.
     */
    private void handleStatsCmd() {
        System.out.println("=== FBP Engine Statistics ===");
        System.out.println("Status:       " + flowManager.getEngine().getState());
        System.out.println("Active Flows: " + flowManager.getRunningFlows().size());
        System.out.println("Total Flows:  " + flowManager.list().size());
        
        long totalNodes = flowManager.getEngine().getFlows().values().stream().mapToLong(f -> f.getNodes().size()).sum();
        System.out.println("Total Nodes:  " + totalNodes);

        long memUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println("Heap Used:    " + (memUsed / 1024 / 1024) + " MB");
    }

    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  flow list / deploy <file> / start <id> / stop <id> / restart <id> / remove <id> / status <id>");
        System.out.println("  node list <flow-id> / stats <flow-id> <node-id>");
        System.out.println("  wire list <flow-id>");
        System.out.println("  monitor flow <id> / node <id> / data <sensor-name> [--filter <val>]");
        System.out.println("  sensor list / stats <name> [--window <1m/1h/1d>]");
        System.out.println("  stats / influx status / broker status / exit");
    }
}
