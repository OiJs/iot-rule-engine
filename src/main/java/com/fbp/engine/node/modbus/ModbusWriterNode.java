package com.fbp.engine.node.modbus;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.io.ProtocolNode;
import com.fbp.engine.protocol.ModbusTcpClient;
import java.io.IOException;
import java.util.Map;

//TODO Stage2 3-7
public class ModbusWriterNode extends ProtocolNode {
    private final String host;
    private final int port;
    private final int slaveId;
    private final int registerAddress;
    private final String valueField;
    private final double scale;
    private ModbusTcpClient client;

    public ModbusWriterNode(String id, Map<String, Object> config) {
        super(id, config);
        this.host = (String) config.getOrDefault("host", "localhost");
        this.port = (int) config.getOrDefault("port", 5020);
        this.slaveId = (int) config.getOrDefault("slaveId", 1);
        this.registerAddress = (int) config.getOrDefault("registerAddress", 0);
        this.valueField = (String) config.get("valueField");
        this.scale = (double) config.getOrDefault("scale", 1.0);

        addInputPort("in");
        addOutputPort("result");
    }

    @Override
    public void connect() throws IOException {
        if (client == null) {
            client = new ModbusTcpClient(host, port);
        }
        if (!client.isConnected()) {
            client.connect();
        }
    }

    @Override
    public void onProcess(Message message) {
        try {
            Object rawValue = message.getPayload().get(valueField);

            if (rawValue != null) {
                // Scale 적용 및 정수 변환 (Modbus 레지스터는 16비트 정수임)
                double numericValue = Double.parseDouble(rawValue.toString());
                int finalValue = (int) (numericValue * scale);

                // Modbus 쓰기 실행 (FC 06)
                client.writeSingleRegister(slaveId, registerAddress, finalValue);

                send("result", message);

                System.out.println(String.format("[ModbusWriter] Node: %s, Addr: %d, Val: %d Write Success",
                                   getId(), registerAddress, finalValue));
            }
        } catch (Exception e) {
            System.err.println(String.format("[ModbusWriter] Error in Node %s: %s", getId(), e.getMessage()));
        }
    }

    @Override
    public void disconnect() {
        if (client != null) {
            try {
                client.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}