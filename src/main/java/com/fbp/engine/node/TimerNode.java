package com.fbp.engine.node;

import com.fbp.engine.message.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimerNode extends AbstractNode {

    private final long intervalMs;
    private int tickCount;
    private ScheduledExecutorService scheduler;

    public TimerNode(String id, long intervalMs) {
        super(id);
        this.intervalMs = intervalMs;
        this.tickCount = 0;
        addOutputPort("out");
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