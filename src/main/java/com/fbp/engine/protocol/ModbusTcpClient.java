package com.fbp.engine.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ModbusTcpClient {
    private final String host;
    private final int port;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private int transactionId = 0;

    public void connect() throws IOException {
        this.socket = new Socket(host, port);
        socket.setSoTimeout(3000);
        this.out = new DataOutputStream(socket.getOutputStream());
        this.in = new DataInputStream(socket.getInputStream());
    }

    public void disconnect() throws IOException {
        if(in != null) in.close();
        if(out != null) out.close();
        if(socket != null) socket.close();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public int getNextTransactionId() {
        return transactionId++;
    }


    public byte[] buildReadRequest(int tid, int unitId, int startAddress, int quantity) {
        byte[] mbap = buildMbapHeader(tid, 6, unitId);
        byte[] pdu = new byte[5];
        pdu[0] = 0x03;
        pdu[1] = (byte) (startAddress >> 8);
        pdu[2] = (byte) (startAddress & 0xFF);
        pdu[3] = (byte) (quantity >> 8);
        pdu[4] = (byte) (quantity & 0xFF);

        byte[] frame = new byte[mbap.length + pdu.length];
        System.arraycopy(mbap, 0, frame, 0, mbap.length);
        System.arraycopy(pdu, 0, frame, mbap.length, pdu.length);
        return frame;
    }

    public byte[] buildWriteRequest(int tid, int unitId, int address, int value) {
        byte[] mbap = buildMbapHeader(tid, 6, unitId);
        byte[] pdu = new byte[5];
        pdu[0] = 0x06;
        pdu[1] = (byte) (address >> 8);
        pdu[2] = (byte) (address & 0xFF);
        pdu[3] = (byte) (value >> 8);
        pdu[4] = (byte) (value & 0xFF);

        byte[] frame = new byte[mbap.length + pdu.length];
        System.arraycopy(mbap, 0, frame, 0, mbap.length);
        System.arraycopy(pdu, 0, frame, mbap.length, pdu.length);
        return frame;
    }

    public int[] readHoldingRegister(int unitId, int startAddress, int quantity) throws IOException, ModbusException {
        int currentId = getNextTransactionId();
        byte[] request = buildReadRequest(currentId, unitId, startAddress, quantity);

        out.write(request);
        out.flush();

        int resId = readMbapHeader();
        if (resId != currentId) throw new IOException("Transaction ID mismatch");

        int resFc = in.readUnsignedByte();
        if (resFc > 0x80) {
            int exceptionCode = in.readUnsignedByte();
            throw new ModbusException(resFc & 0x7F, exceptionCode);
        }

        int byteCount = in.readUnsignedByte();
        int[] values = new int[quantity];
        for (int i = 0; i < quantity; i++) {
            values[i] = in.readUnsignedShort();
        }
        return values;
    }

    public void writeSingleRegister(int unitId, int address, int value) throws IOException, ModbusException {
        int currentId = getNextTransactionId();
        byte[] request = buildWriteRequest(currentId, unitId, address, value);

        out.write(request);
        out.flush();

        int resId = readMbapHeader();
        if (resId != currentId) throw new IOException("Transaction ID mismatch");

        int resFc = in.readUnsignedByte();

        if (resFc > 0x80) {
            int exceptionCode = in.readUnsignedByte();
            throw new ModbusException(resFc & 0x7F, exceptionCode);
        }

        int resAddr = in.readUnsignedShort();
        int resVal = in.readUnsignedShort();

        if(resAddr != address || resVal != value) {
            throw new IOException("Echo back verification failed");
        }
    }

    private byte[] buildMbapHeader(int transactionId, int length, int unitId) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeShort(transactionId);
            dos.writeShort(0); // Protocol ID: 0x0000
            dos.writeShort(length);
            dos.writeByte(unitId);
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }

    private int readMbapHeader() throws IOException {
        int tid = in.readUnsignedShort();
        in.readUnsignedShort(); // Protocol ID skip
        in.readUnsignedShort(); // Length skip
        in.readUnsignedByte();  // Unit ID skip
        return tid;
    }
}