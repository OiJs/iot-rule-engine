package com.fbp.engine.node.modbus;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.io.ProtocolNode;
import com.fbp.engine.protocol.ModbusTcpClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
//TODO Stage2 3-6

public class ModbusReaderNode extends ProtocolNode {
    private final String host;
    private final int port;
    private final int slaveId;
    private final int startAddress;
    private final int count;
    private final Map<String, Object> registerMapping;
    private ModbusTcpClient client;

    public ModbusReaderNode(String id, Map<String, Object> config) {
        super(id, config);
        this.host = (String) config.getOrDefault("host", "localhost");
        this.port = (int) config.getOrDefault("port", 502);
        this.slaveId = (int) config.getOrDefault("slaveId", 1);
        this.startAddress = (int) config.getOrDefault("startAddress", 0);
        this.count = (int) config.getOrDefault("count", 1);
        this.registerMapping = (Map<String, Object>) config.get("registerMapping");

        addInputPort("trigger");
        addOutputPort("out");
        addOutputPort("error");
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
