package com.fbp.engine.flowtest;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;
import com.fbp.engine.node.modbus.ModbusReaderNode;
import com.fbp.engine.protocol.ModbusTcpSimulator;
import java.util.Map;

//TODO Stage2 3-8
public class ModbusFlow {
    public static void main(String[] args) {
        int port = 5020;
        ModbusTcpSimulator simulator = new ModbusTcpSimulator(port, 10);
        simulator.setRegister(0, 255);
        simulator.setRegister(1, 600);
        simulator.start();

        FlowEngine engine = new FlowEngine();

        TimerNode timer = new TimerNode("timer", 1000);
        ModbusReaderNode modbusReaderNode = new ModbusReaderNode("reader", Map.of(
                "host", "localhost",
                "port", port,
                "slaveId", 1,
                "startAddress", 0,
                "count", 2
        ));

        PrintNode printNode = new PrintNode("printer");

        Flow flow = new Flow("modbusFlow")
                .addNode(timer)
                .addNode(modbusReaderNode)
                .addNode(printNode)
                .connect("timer", "out", "reader", "trigger")
                .connect("reader", "out", "printer", "in");

        engine.register(flow);

        engine.startFlow("modbusFlow");
    }
}
