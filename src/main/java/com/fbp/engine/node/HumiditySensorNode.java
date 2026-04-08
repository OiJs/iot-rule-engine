package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.util.HashMap;
import java.util.Map;

public class HumiditySensorNode extends AbstractNode{

    private double min;
    private double max;

    public HumiditySensorNode(String id, double min, double max) {
        super(id);
        this.min = min;
        this.max = max;
        addInputPort("trigger");
        addOutputPort("out");
    }

    @Override
    protected void onProcess(Message message) {
        double rawHumidity = min + Math.random() * (max - min);
        double humidity = Math.round(rawHumidity * 10.0) / 10.0;

        Map<String, Object> payload = new HashMap<>();
        payload.put("sensorId", getId());
        payload.put("humidity", humidity);
        payload.put("unit", "%");
        payload.put("timestamp", System.currentTimeMillis());

        Message msg = new Message(payload);

        send("out", msg);
    }
}
