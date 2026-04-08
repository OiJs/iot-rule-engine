package com.fbp.engine.flowtest;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.*;

public class Step9_7Flow {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 과제 9-5: 온도, 습도 모니터링 시스템 시작 ===");

        FlowEngine engine = new FlowEngine();

        TimerNode timer = new TimerNode("timer", 1000);

        TemperatureSensorNode temperatureSensor = new TemperatureSensorNode("tempSensor", 15.0, 45.0);
        ThresholdFilterNode temperatureFilter = new ThresholdFilterNode("tempFilter","temperature", 30.0);

        HumiditySensorNode humiditySensor = new HumiditySensorNode("humiditySensor", 30, 90);
        ThresholdFilterNode humidityFilter = new ThresholdFilterNode("humidityFilter", "humidity", 70);

        AlertNode alert = new AlertNode("alert-node");
        PrintNode logger = new PrintNode("normal-log");

        Flow monitoringFlow = new Flow("temp-humidity-monitor")
                .addNode(timer)
                .addNode(temperatureSensor)
                .addNode(temperatureFilter)

                .addNode(humiditySensor)
                .addNode(humidityFilter)

                .addNode(alert)
                .addNode(logger)

                .connect("timer", "out", "tempSensor", "trigger")
                .connect("timer", "out", "humiditySensor", "trigger")

                .connect("tempSensor", "out", "tempFilter", "in")
                .connect("tempFilter", "alert", "alert-node", "in")
                .connect("tempFilter", "normal", "normal-log", "in")

                .connect("humiditySensor", "out", "humidityFilter", "in")
                .connect("humidityFilter", "alert", "alert-node", "in")
                .connect("humidityFilter", "normal", "normal-log", "in");

        engine.register(monitoringFlow);
        engine.startFlow("temp-humidity-monitor");

        System.out.println(">>> 모니터링 중... (10초 후 종료)");

        Thread.sleep(10000);

        System.out.println("\n>>> 시뮬레이션 종료 시퀀스 가동");
        engine.shutdown();
        System.out.println("=== 시스템 종료 완료 ===");
    }
}