package com.fbp.engine.node;

import com.fbp.engine.message.Message;

public class AlertNode extends AbstractNode{

    public AlertNode(String id) {
        super(id);
        addInputPort("in");
    }

    @Override
    protected void onProcess(Message message) {
        String sensorId = message.get("sensorId");
        if(sensorId == null) {
            System.out.println("알 수 없는 센서 데이터");
            return;
        }
        boolean hasData = false;

        Double temperature = message.get("temperature");
        Double humidity = message.get("humidity");

        StringBuilder alert = new StringBuilder();

        alert.append(String.format("[경고] 센서 {%s}", sensorId));

        if(temperature != null) {
            alert.append(String.format("온도 {%.1f}°C ", temperature));
            hasData = true;
        }

        if(humidity != null) {
            alert.append(String.format("습도 {%.1f}°C ", humidity));
            hasData = true;
        }

        if (hasData) {
            alert.append("— 임계값 초과!");
            System.out.println(alert);
        }
    }
}
