package com.fbp.engine.api;

import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.metrics.MetricsAggregator;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.node.TimerNode;
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
import java.util.Map;

class HttpApiServerTest {
    private HttpApiServer server;
    private HttpClient client;
    private MetricsCollector collector;
    private FlowManager manager;
    private FlowEngine engine;
    private NodeRegistry nodeRegistry;
    private final int PORT = 8888;
    private final String BASE_URL = "http://localhost:" + PORT;

    @BeforeEach
    void setUp() throws Exception {
        collector = new MetricsCollector(new MetricsAggregator());
        engine = new FlowEngine();
        nodeRegistry = new NodeRegistry();
        manager = new FlowManager(engine, nodeRegistry);

        nodeRegistry.register("timer", (id, config) -> {
            long interval = 1000;
            if (config != null && config.containsKey("interval")) {
                interval = ((Number) config.get("interval")).longValue();
            }
            return new TimerNode(id, Map.of("intervalMs", interval));
        });

        manager.addParser(new JsonFlowParser());
        server = new HttpApiServer(PORT, manager, collector);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        engine.shutdown();
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
        String validJson = """
                {
                  "id": "flow-1",
                  "transport": { "type": "local" },
                  "nodes": [
                    { "id": "n1", "type": "timer", "config": { "interval": 1000 } }
                  ],
                  "connections": []
                }
                """;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/flows?format=json"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(validJson))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(201, response.statusCode());
    }

    @Test
    void testPostFlowsBadRequest() throws IOException, InterruptedException {
        String invalidJson = "{ \"wrong\": \"format\" }";
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
        String validJson = """
            {
              "id": "delete-me",
              "transport": { "type": "local" },
              "nodes": [ { "id": "n1", "type": "timer", "config": {"interval": 1000} } ],
              "connections": []
            }
            """;
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/flows?format=json"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(validJson))
                .build();
        client.send(postRequest, HttpResponse.BodyHandlers.ofString());

        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/flows/delete-me"))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode());
    }

    @Test
    void testDeleteFlowNotFound() throws IOException, InterruptedException {
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
        Assertions.assertTrue(response.headers().firstValue("Content-Type").get().contains("application/json"));
    }
}