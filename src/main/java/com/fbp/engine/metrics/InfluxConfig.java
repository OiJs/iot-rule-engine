package com.fbp.engine.metrics;

import lombok.Data;

@Data
public class InfluxConfig {
    private String url = "http://localhost:8086";
    private String token;
    private String org = "fbp";
    private String bucket = "fbp-metrics";
    
    private BatchConfig batch = new BatchConfig();
    private RetryConfig retry = new RetryConfig();
    private BufferConfig buffer = new BufferConfig();

    @Data
    public static class BatchConfig {
        private int size = 1000;
        private int flushIntervalMs = 1000;
    }

    @Data
    public static class RetryConfig {
        private int maxAttempts = 5;
        private int initialBackoffMs = 200;
    }

    @Data
    public static class BufferConfig {
        private String type = "memory"; 
        private int maxSize = 100000;
    }
}