package com.fbp.engine.api;

import com.fbp.engine.core.FlowNotFoundException;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.parser.FlowParserException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import lombok.RequiredArgsConstructor;

//TODO MetricsHandler + FlowHandler 통합
@RequiredArgsConstructor
public class FlowHandler implements HttpHandler {
    private final FlowManager manager;
    private final MetricsCollector collector;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        String method = exchange.getRequestMethod();

        try {
            // 1. /flows (목록 조회 또는 생성)
            if (parts.length == 2 && "flows".equals(parts[1])) {
                handleRootFlows(exchange, method);
            }
            // 2. /flows/{id} (삭제)
            else if (parts.length == 3 && "flows".equals(parts[1])) {
                handleSpecificFlow(exchange, method, parts[2]);
            }
            // 3. /flows/{id}/metrics (플로우별 전체 메트릭)
            else if (parts.length == 4 && "metrics".equals(parts[3])) {
                handleFlowMetrics(exchange, method, parts[2]);
            }
            // 4. /flows/{id}/nodes/{nodeId}/stats (노드별 상세 통계)
            else if (parts.length == 6 && "nodes".equals(parts[3]) && "stats".equals(parts[5])) {
                handleNodeStats(exchange, method, parts[2], parts[4]);
            }
            else {
                ApiResponse.error(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            ApiResponse.error(exchange, 500, "Internal Server Error: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private void handleRootFlows(HttpExchange exchange, String method) throws IOException {
        if ("GET".equals(method)) {
            ApiResponse.send(exchange, 200, manager.getRunningFlows());
        } else if ("POST".equals(method)) {
            try (InputStream in = exchange.getRequestBody()) {
                // 1. 포맷 결정 (QueryParam 우선 -> 그 다음 Content-Type)
                String format = getFormat(exchange);

                // 2. 배포 시도
                String flowId = manager.deploy(format, in);

                ApiResponse.send(exchange, 201, Map.of(
                        "message", "Flow deployed successfully",
                        "flowId", flowId
                ));
            } catch (Exception e) {
                // 3. 에러 핸들링 세분화
                // FlowParserException이거나 Jackson 관련 예외인 경우 400 반환
                if (isBadRequest(e)) {
                    ApiResponse.error(exchange, 400, "Invalid Request: " + e.getMessage());
                    System.err.println("Client Error (400): " + e.getMessage());
                } else {
                    // 그 외 예상치 못한 에러는 500
                    ApiResponse.error(exchange, 500, "Internal Server Error");
                    e.printStackTrace();
                }
            }
        } else {
            ApiResponse.error(exchange, 405, "Method Not Allowed");
        }
    }

    private String getFormat(HttpExchange exchange) {
        // 1. Query String 확인 (?format=json)
        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.contains("format=")) {
            return query.split("format=")[1].split("&")[0];
        }

        // 2. Content-Type 확인 (application/json -> json)
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && contentType.contains("application/json")) {
            return "json";
        }

        return "json"; // 기본값
    }

    // 400 에러로 처리할 예외인지 판단
    private boolean isBadRequest(Exception e) {
        String msg = e.getMessage();
        return e instanceof FlowParserException ||
                (msg != null && (msg.contains("지원하지 않는 포맷") || msg.contains("Unrecognized field")));
    }

    // [DELETE /flows/{id}]
    private void handleSpecificFlow(HttpExchange exchange, String method, String flowId) throws IOException {
        if ("DELETE".equals(method)) {
            try {
                manager.remove(flowId);
                ApiResponse.send(exchange, 200, Map.of("message", "Flow removed", "id", flowId));
            } catch (FlowNotFoundException e) {
                ApiResponse.error(exchange, 404, e.getMessage());
            }
        } else {
            ApiResponse.error(exchange, 405, "Method Not Allowed");
        }
    }

    // [GET /flows/{id}/metrics]
    private void handleFlowMetrics(HttpExchange exchange, String method, String flowId) throws IOException {
        if (!"GET".equals(method)) {
            ApiResponse.error(exchange, 405, "Method Not Allowed");
            return;
        }

        var metrics = collector.getFlowMetrics(flowId);
        if (metrics == null || metrics.isEmpty()) {
            ApiResponse.error(exchange, 404, "Metrics for flow '" + flowId + "' not found");
        } else {
            ApiResponse.send(exchange, 200, metrics);
        }
    }

    // [GET /flows/{id}/nodes/{nodeId}/stats]
    private void handleNodeStats(HttpExchange exchange, String method, String flowId, String nodeId) throws IOException {
        if (!"GET".equals(method)) {
            ApiResponse.error(exchange, 405, "Method Not Allowed");
            return;
        }

        var stats = collector.getSnapshot(flowId, nodeId);
        if (stats == null) {
            ApiResponse.error(exchange, 404, "Stats for node '" + nodeId + "' in flow '" + flowId + "' not found");
        } else {
            ApiResponse.send(exchange, 200, stats);
        }
    }

    private String extractFormat(String contentType) {
        if (contentType == null) return "json";

        String lowerType = contentType.toLowerCase();

        if (lowerType.contains("application/json")) return "json";
        if (lowerType.contains("application/x-yaml") || lowerType.contains("text/yaml")) return "yml";

        return "json";
    }
}
