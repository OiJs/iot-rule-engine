package com.fbp.engine.metrics;

public class TimeWindowBucketer {
    private final String windowType; // "1m", "1h", "1d"
    private final long durationMs;
    private long lastFlushTime;

    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;
    private double sum = 0;
    private long count = 0;

    public TimeWindowBucketer(String windowType) {
        this.windowType = windowType;
        this.durationMs = parseDuration(windowType);
        this.lastFlushTime = (System.currentTimeMillis() / durationMs) * durationMs;
    }

    private long parseDuration(String type) {
        return switch (type) {
            case "1m" -> 60000;
            case "1h" -> 3600000;
            case "1d" -> 86400000;
            default -> 60000;
        };
    }

    public synchronized boolean isOverdue(long timestamp) {
        return timestamp >= lastFlushTime + durationMs;
    }

    public synchronized void add(double value) {
        min = Math.min(min, value);
        max = Math.max(max, value);
        sum += value;
        count++;
    }

    public synchronized BucketSnapshot flush(long newTimestamp) {
        BucketSnapshot snapshot = new BucketSnapshot(
            min == Double.MAX_VALUE ? 0 : min,
            max == Double.MIN_VALUE ? 0 : max,
            count == 0 ? 0 : sum / count,
            count,
            lastFlushTime
        );
        reset(newTimestamp);
        return snapshot;
    }

    private void reset(long timestamp) {
        min = Double.MAX_VALUE;
        max = Double.MIN_VALUE;
        sum = 0;
        count = 0;
        lastFlushTime = (timestamp / durationMs) * durationMs;
    }

    public String getWindowType() {
        return windowType;
    }

    public record BucketSnapshot(double min, double max, double avg, long count, long timestamp) {}
}
