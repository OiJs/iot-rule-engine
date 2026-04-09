package com.fbp.engine.node.io;

import com.fbp.engine.message.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

public class EchoProtocolNode extends ProtocolNode{
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public EchoProtocolNode(String id, Map<String, Object> config, Socket socket) {
        super(id, config);
        this.socket = socket;
        addInputPort("in");
        addOutputPort("out");
    }

    @Override
    protected void connect() throws Exception {
        String host = (String)getConfig("host");
        int port = (Integer) getConfig("port");

        socket = new Socket(host, port);

        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    @Override
    protected void disconnect() throws Exception {
        if(in != null) in.close();
        if(out != null) out.close();
        if(socket != null) socket.close();
    }

    @Override
    protected void onProcess(Message message) {
        try {
            String dataToSend = message.get("data").toString();
            out.println(dataToSend);

            String response = in.readLine();

            Message outMessage = new Message(Map.of(
                    "echoResponse", response,
                    "timestamp", System.currentTimeMillis()
            ));

            send("out", outMessage);
        } catch (IOException e) {
            System.err.println("[" + getId() + "] 통신 중 에러 발생: " + e.getMessage());
            reconnect();
        }
    }
}
