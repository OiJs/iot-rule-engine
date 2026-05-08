package com.fbp.engine.node.modbus;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.io.ProtocolNode;
import com.fbp.engine.protocol.ModbusTcpClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
//TODO Stage2 3-6

public class ModbusReaderNode extends ProtocolNode {
    private String host;
    private int port;
    private int slaveId;
    private int startAddress;
    private int count;
    private Map<String, Object> registerMapping;
    private ModbusTcpClient client;

    public ModbusReaderNode(String id, Map<String, Object> config) {
        super(id, config);
        syncConfig(config);

        addInputPort("trigger");
        addOutputPort("out");
        addOutputPort("error");
    }

    private void syncConfig(Map<String, Object> cfg) {
        this.host = (String) cfg.getOrDefault("host", "localhost");
        this.port = ((Number) cfg.getOrDefault("port", 502)).intValue();
        this.slaveId = ((Number) cfg.getOrDefault("slaveId", 1)).intValue();
        this.startAddress = ((Number) cfg.getOrDefault("startAddress", 0)).intValue();
        this.count = ((Number) cfg.getOrDefault("count", 1)).intValue();
        this.registerMapping = (Map<String, Object>) cfg.get("registerMapping");
    }

    @Override
    protected void onConfigUpdate(Map<String, Object> newConfig) {
        //설정 변경 시 필드 동기화 및 재연결 판단
        String oldHost = this.host;
        int oldPort = this.port;

        syncConfig(newConfig);

        if (!host.equals(oldHost) || port != oldPort) {
            System.out.println("[" + getId() + "] 호스트/포트 변경 감지 -> 재연결");
            shutdown();  // 기존 연결 종료
            initialize(); // 새 연결 시도
        }
    }


    @Override
    protected void connect() throws Exception {
        if(client == null) {
            client = new ModbusTcpClient(host, port);
        }
        if(!client.isConnected()) {
            client.connect();
        }
    }

    @Override
    protected void onProcess(Message message) {
        try {
            int[] results = client.readHoldingRegister(slaveId, startAddress, count);

            Map<String, Object> payload = new HashMap<>();
            if(registerMapping != null) {
                for(Map.Entry<String, Object> entry : registerMapping.entrySet()) {
                    int index = Integer.parseInt(entry.getValue().toString());

                    if(index < results.length) {
                        payload.put(entry.getKey(), results[index]);
                    }
                }
            } else {
                payload.put("data", Arrays.toString(results));
            }
            send("out", new Message(payload));
        }catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("status", "error");
            errorMap.put("message", e.getMessage());
            errorMap.put("nodeId", getId());

            send("error", new Message(errorMap));
        }
    }


    @Override
    protected void disconnect() throws Exception {

    }
}
