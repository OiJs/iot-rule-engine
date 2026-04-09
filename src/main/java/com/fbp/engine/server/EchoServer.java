package com.fbp.engine.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class EchoServer {
    public static void main(String[] args) {
        int port = 8888;

        try(ServerSocket serverSocket = new ServerSocket(port)) {
            while(true) {
                try (Socket clientSocket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                     PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                    System.out.println("클라이언트 연결: " + clientSocket.getInetAddress());

                    String inputLine;

                    while((inputLine = in.readLine()) != null) {
                        out.println(inputLine);
                    }
                } catch (IOException e) {
                    System.err.println("클라이언트 통신 오류 발생: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("서버 시작 오류: " + e.getMessage());
        }
    }
}
