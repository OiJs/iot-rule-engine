package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileWriteNode extends AbstractNode{

    private final String filePath;
    private BufferedWriter writer;

    public FileWriteNode(String id, String filePath) {
        super(id);
        this.filePath = filePath;
        addInputPort("in");
    }

    @Override
    public void initialize() {
        File file = new File(filePath);
        File parentDir = file.getParentFile();

        if(parentDir != null && !parentDir.exists()) {
            if(parentDir.mkdirs()) {
                System.out.println("[" + getId() + "] 디렉토리 생성: " + parentDir);
            } else {
                System.err.println("[" + getId() + "] 디렉토리 생성 실패");
            }
        }

        try {
            writer = new BufferedWriter(new FileWriter(filePath, true));
        } catch (IOException e) {
            System.err.println("[" + getId() + "] 파일 열기 오류" + e.getMessage() );
        }
    }

    @Override
    public void shutdown() {
        try {
            if(writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("[" + getId() + "] 파일 닫기 오류" + e.getMessage() );
        }
    }

    @Override
    protected void onProcess(Message message) {
        if(writer == null) return;

        try {
            writer.write(message.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[" + getId() + "] 쓰기 오류" + e.getMessage() );
        }
    }
}
