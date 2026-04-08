package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileWriterNodeTest {

    private FileWriteNode writerNode;
    private final String TEST_FILE_PATH = "logs/test_output.log";

    @BeforeEach
    void setUp() throws IOException {
        Files.deleteIfExists(Paths.get(TEST_FILE_PATH));
        writerNode = new FileWriteNode("file-writer-01", TEST_FILE_PATH);
    }

    @AfterEach
    void tearDown() throws IOException {
        writerNode.shutdown();
        Files.deleteIfExists(Paths.get(TEST_FILE_PATH));
    }

    @Test
    @DisplayName("1. 파일 생성 확인: initialize() 후 파일이 물리적으로 생성되는가")
    void test1_FileCreation() {
        writerNode.initialize();

        File file = new File(TEST_FILE_PATH);
        assertTrue(file.exists(), "지정한 경로에 파일이 생성되지 않았습니다.");
    }

    @Test
    @DisplayName("2. 내용 기록 확인: 메시지 3개를 보냈을 때 3줄이 기록되는가")
    void test2_ContentLogging() throws IOException, InterruptedException {
        writerNode.initialize();

        writerNode.onProcess(new Message(Map.of("data", "First Log")));
        writerNode.onProcess(new Message(Map.of("data", "Second Log")));
        writerNode.onProcess(new Message(Map.of("data", "Third Log")));

        writerNode.shutdown();

        Path path = Paths.get(TEST_FILE_PATH);
        List<String> lines = Files.readAllLines(path);

        assertEquals(3, lines.size(), "기록된 줄 수가 메시지 개수와 일치하지 않습니다.");
        assertTrue(lines.get(0).contains("First Log"));
    }

    @Test
    @DisplayName("3. Shutdown 후 동작 확인: 종료된 후에는 더 이상 기록되지 않아야 함")
    void test3_PostShutdown() throws IOException {
        writerNode.initialize();
        writerNode.shutdown();

        assertDoesNotThrow(() -> {
            writerNode.onProcess(new Message(Map.of("data", "After Shutdown")));
        });

        List<String> lines = Files.readAllLines(Paths.get(TEST_FILE_PATH));
        assertTrue(lines.isEmpty() || !lines.toString().contains("After Shutdown"),
                "shutdown() 이후에도 데이터가 기록되었습니다.");
    }
}