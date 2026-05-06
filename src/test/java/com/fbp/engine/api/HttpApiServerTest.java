package com.fbp.engine.api;

import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.parser.FlowParser;
import com.fbp.engine.parser.JsonFlowParser;
import com.fbp.engine.registry.NodeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class HttpApiServerTest {
    private HttpApiServer server;
    private HttpClient client;
    private MetricsCollector collector;
    private FlowManager manager;
    private FlowEngine engine;
    private final int PORT = 8888;
    private final String BASE_URL = "http://localhost:" + PORT;

    @BeforeEach
    void setUp() throws Exception {
        collector = new MetricsCollector();
        engine = new FlowEngine();
        manager = new FlowManager(engine);

        NodeRegistry registry = new NodeRegistry();
        FlowParser parser = new JsonFlowParser(registry);


        server = new HttpApiServer(PORT, manager, collector);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void testServerStartStop() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());
    }

    @Test
    void testGetHealth() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("\"status\""));
    }

    @Test
    void testGetFlows() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/flows"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertNotNull(response.body());
    }

    @Test
    void testPostFlowsSuccess() throws IOException, InterruptedException {
        // 3. 실제 Parser가 수용 가능한 유효한 FBP JSON 구조
        String validJson = """
                {
                  "id": "flow-1",
                  "nodes": [
                    { "id": "n1", "type": "timer", "config": { "interval": 1000 } }
                  ],
                  "connections": []
                }
                """;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/flows"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(validJson))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, response.statusCode());
    }

    @Test
    void testPostFlowsBadRequest() throws IOException, InterruptedException {
        String invalidJson = "{ \"wrong\": \"format\" }"; // ID가 없는 등 부적절한 구조
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/flows"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(400, response.statusCode());
    }

    @Test
    void testDeleteFlowSuccess() throws IOException, InterruptedException {
        // 실제 존재하는 ID를 지우는 시나리오 (필요시 미리 POST 수행)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/flows/existing-id"))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());
    }

    @Test
    void testDeleteFlowNotFound() throws IOException, InterruptedException {
        // 존재하지 않는 ID 삭제 시 404를 기대하도록 Manager 로직 확인 필요
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/flows/non-existent-id"))
                .DELETE()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(404, response.statusCode());
    }

    @Test
    void testGetFlowMetrics() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/flows/flow-1/metrics"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertTrue(response.statusCode() == 200 || response.statusCode() == 404);
    }

    @Test
    void testNotFoundPath() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/invalid/path"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(404, response.statusCode());
    }

    @Test
    void testMethodNotAllowed() throws IOException, InterruptedException {
        // GET만 허용되는 /health에 POST 요청
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(405, response.statusCode());
    }

    @Test
    void testPortConflict() {
        Assertions.assertThrows(IOException.class, () -> {
            HttpApiServer secondServer = new HttpApiServer(PORT, manager, collector);
            secondServer.start();
        });
    }

    @Test
    void testContentTypeHeader() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // 5. ApiResponse.send에서 설정한 헤더 값 검증
        Assertions.assertTrue(response.headers().firstValue("Content-Type").get().contains("application/json"));
    }
}