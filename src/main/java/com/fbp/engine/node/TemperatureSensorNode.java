package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.HashMap;
import java.util.Map;

public class TemperatureSensorNode extends AbstractNode{
    private double min;
    private double max;

    public TemperatureSensorNode(String id, double min, double max) {
        super(id);
        this.min = min;
        this.max = max;
        addInputPort("trigger");
        addOutputPort("out");
    }

    @Override
    protected void onProcess(Message message) {
        double rawTemp = min + Math.random() * (max - min);
        double temperature = Math.round(rawTemp * 10.0) / 10.0;

        Map<String, Object> payload = new HashMap<>();

        payload.put("sensorId", getId());
        payload.put("temperature", temperature);
        payload.put("unit", "°C");
        payload.put("timestamp", System.currentTimeMillis());

        Message outMsg = new Message(payload);

        send("out", outMsg);
    }
}
