package com.fbp.engine.node.io;

import com.fbp.engine.message.Message;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

public class EchoProtocolNode extends ProtocolNode {
    private String host;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public EchoProtocolNode(String id, Map<String, Object> config) {
        super(id, config);
        syncConfig(config);
        addInputPort("in");
        addOutputPort("out");
    }

    @Override
    protected void onConfigUpdate(Map<String, Object> newConfig) {
        super.onConfigUpdate(newConfig); // ProtocolNode의 reconnectIntervalMs 등 업데이트

        String oldHost = this.host;
        int oldPort = this.port;
        syncConfig(newConfig);

        // 접속 대상이 바뀌면 재연결
        if (!host.equals(oldHost) || port != oldPort) {
            shutdown();
            initialize();
        }
    }

    private void syncConfig(Map<String, Object> cfg) {
        this.host = (String) cfg.getOrDefault("host", "127.0.0.1");
        this.port = ((Number) cfg.getOrDefault("port", 9000)).intValue();
        this.config.put("host", this.host);
        this.config.put("port", this.port);
    }

    @Override
    protected void connect() throws Exception {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    @Override
    protected void disconnect() throws Exception {
        if (in != null) in.close();
        if (out != null) out.close();
        if (socket != null) socket.close();
    }

    @Override
    protected void onProcess(Message message) {
        if (!isConnected()) return;
        try {
            String data = message.get("data").toString();
            out.println(data);
            String response = in.readLine();
            send("out", new Message(Map.of("echoResponse", response, "ts", System.currentTimeMillis())));
        } catch (Exception e) {
            reconnect();
        }
    }
}