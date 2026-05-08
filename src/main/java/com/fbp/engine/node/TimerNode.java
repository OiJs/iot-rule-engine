package com.fbp.engine.node;

import com.fbp.engine.message.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimerNode extends AbstractNode {

    private long intervalMs;
    private int tickCount;
    private ScheduledExecutorService scheduler;

    public TimerNode(String id, Map<String, Object> config) {
        super(id);
        syncConfig(config);
        addOutputPort("out");
    }

    private void syncConfig(Map<String, Object> cfg) {
        this.intervalMs = ((Number) cfg.getOrDefault("intervalMs", 1000L)).longValue();
    }

    @Override
    protected void onConfigUpdate(Map<String, Object> newConfig) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Message msg = new Message(Map.of("tick", tickCount++, "ts", System.currentTimeMillis()));
            send("out", msg);
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void initialize() {
        super.initialize();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("tick", tickCount);
            payload.put("timestamp", System.currentTimeMillis());
            Message msg = new Message(payload);
            send("out", msg);
            tickCount++;
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    protected void onProcess(Message message) {}
}