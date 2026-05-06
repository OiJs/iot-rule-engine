package com.fbp.engine.api;

import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.metrics.MetricsCollector;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class HttpApiServer {
    private final HttpServer server;

    public HttpApiServer(int port, FlowManager manager, MetricsCollector collector) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/health", new HealthHandler(manager));
        server.createContext("/flows", new FlowHandler(manager, collector));

        server.setExecutor(Executors.newFixedThreadPool(10));
    }

    public void start() {
        server.start();
        System.out.println("API Server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }
}
