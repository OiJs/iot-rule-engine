package com.fbp.engine.node.modbus;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.io.ProtocolNode;
import com.fbp.engine.protocol.ModbusTcpClient;
import java.io.IOException;
import java.util.Map;

public class ModbusWriterNode extends ProtocolNode {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 502;

    private String host;
    private int port;
    private int slaveId;
    private int registerAddress;
    private String valueField;
    private double scale;
    private ModbusTcpClient client;

    public ModbusWriterNode(String id, Map<String, Object> config) {
        super(id, config);
        syncConfig(config);

        addInputPort("in");
        addOutputPort("result");
    }

    @Override
    protected void onConfigUpdate(Map<String, Object> newConfig) {
        super.onConfigUpdate(newConfig);

        String oldHost = this.host;
        int oldPort = this.port;
        syncConfig(newConfig);

        // 접속 정보가 바뀌면 클라이언트 재설정
        if (!host.equals(oldHost) || port != oldPort) {
            shutdown();
            client = null; // 새 호스트로 클라이언트 재생성 유도
            initialize();
        }
    }

    private void syncConfig(Map<String, Object> cfg) {
        this.host = (String) cfg.getOrDefault("host", DEFAULT_HOST);
        this.port = ((Number) cfg.getOrDefault("port", DEFAULT_PORT)).intValue();
        this.slaveId = ((Number) cfg.getOrDefault("slaveId", 1)).intValue();
        this.registerAddress = ((Number) cfg.getOrDefault("registerAddress", 0)).intValue();
        this.valueField = (String) cfg.get("valueField");
        this.scale = ((Number) cfg.getOrDefault("scale", 1.0)).doubleValue();

        this.config.put("host", this.host);
        this.config.put("port", this.port);
        this.config.put("scale", this.scale);
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
    public void disconnect() throws IOException {
        if (client != null) {
            client.disconnect();
        }
    }

    @Override
    public void onProcess(Message message) {
        if (!isConnected()) return;

        try {
            Object rawValue = message.getPayload().get(valueField);
            if (rawValue != null) {
                double numericValue = Double.parseDouble(rawValue.toString());
                int finalValue = (int) (numericValue * scale);

                client.writeSingleRegister(slaveId, registerAddress, finalValue);
                send("result", message);

                System.out.println(String.format("[ModbusWriter] Node: %s, Addr: %d, Val: %d (Scale: %.2f)",
                        getId(), registerAddress, finalValue, scale));
            }
        } catch (Exception e) {
            System.err.println("[" + getId() + "] 쓰기 에러: " + e.getMessage());
        }
    }
}