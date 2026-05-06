package com.fbp.engine.flowtest;

import com.fbp.engine.core.FlowEngine;
import java.util.concurrent.atomic.AtomicInteger;

//TODO Stage2 5-3
public class FbpIntegratedTest {
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final int MODBUS_PORT = 5020;
    private static final AtomicInteger processedCount = new AtomicInteger(0);

    public static void main(String[] args) {
        FlowEngine engine = new FlowEngine();


    }
}
