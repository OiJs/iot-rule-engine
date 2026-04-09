package com.fbp.engine.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class EchoClient {
    public static void main(String[] args) {
        String host = "localhost";
        int port = 8888;

        try(Socket socket = new Socket(host, port);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String message = "Hello FBP";
            out.println(message);

            String response = in.readLine();
            System.out.println("echo server: " + response);
        } catch (IOException e) {
            System.err.println("네트워크 I/O 오류 (서버가 켜져 있는지 확인하세요): " + e.getMessage());        }
    }
}
