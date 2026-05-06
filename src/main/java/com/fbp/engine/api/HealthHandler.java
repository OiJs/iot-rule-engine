package com.fbp.engine.api;

import com.fbp.engine.engine.FlowManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.Map;

public class HealthHandler implements HttpHandler {
    private final long startTime = System.currentTimeMillis();
    private final FlowManager manager;

    public HealthHandler(FlowManager manager) {
        this.manager = manager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if(!"GET".equals(exchange.getRequestMethod())) {
            ApiResponse.error(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            Map<String, Object> health = Map.of(
                    "status", manager.getEngine().getState(),
                    "uptime", (System.currentTimeMillis() - startTime) / 1000 + "s",
                    "flowCount", manager.list().size()
            );
            ApiResponse.send(exchange, 200, health);
        } catch (Exception e) {
            ApiResponse.error(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }
}
