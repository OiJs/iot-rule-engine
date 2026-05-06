package com.fbp.engine.test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

public class MemoryMonitor {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final List<Long> usageHistory = new ArrayList<>();
    private final Runtime runtime = Runtime.getRuntime();

    public void start(int intervalSeconds) {
        scheduler.scheduleAtFixedRate(() -> {
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            usageHistory.add(usedMemory);
            System.out.printf("[MemoryMonitor] Used: %d MB | Total: %d MB\n", 
                              usedMemory, runtime.totalMemory() / (1024 * 1024));
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    public List<Long> getUsageHistory() {
        return new ArrayList<>(usageHistory);
    }

    public boolean isMemoryStable() {
        if (usageHistory.size() < 5) return true;
        int count = usageHistory.size();
        return !(usageHistory.get(count - 1) > usageHistory.get(count - 5));
    }
}