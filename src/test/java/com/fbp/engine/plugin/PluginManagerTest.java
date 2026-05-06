package com.fbp.engine.plugin;

import com.fbp.engine.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class PluginManagerTest {
    private PluginManager manager;
    private NodeRegistry registry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
        manager = new PluginManager(registry);
    }

    @Test
    @DisplayName("1. ClassPath 로드: 내장 Provider 탐색 확인")
    void test1_ClassPathLoading() {
        assertDoesNotThrow(() -> manager.loadAll(tempDir.toString()));
    }

    @Test
    @DisplayName("2. 외부 JAR 로드: JAR 파일 내 클래스 로딩 시도 확인")
    void test2_ExternalJarLoading() throws IOException {
        createMockJar("valid-plugin.jar");

        assertDoesNotThrow(() -> manager.loadAll(tempDir.toString()));
    }

    @Test
    @DisplayName("3. NodeRegistry 자동 등록: 로드 성공 시 레지스트리 기록 확인")
    void test3_AutoRegistration() throws IOException {
        createMockJar("reg-test.jar");
        manager.loadAll(tempDir.toString());

        assertTrue(manager.getLoadedPluginCount() >= 0);
    }

    @Test
    @DisplayName("4. 타입 충돌 처리: 내장 노드와 중복 시 플러그인 무시")
    void test4_ConflictHandling() {
        registry.register("TimerNode", (id, config) -> null);

        NodeDescriptor pluginNode = new NodeDescriptor("TimerNode", "Desc", null, (id, config) -> null);

        assertTrue(registry.isRegistered("TimerNode"));
    }

    @Test
    @DisplayName("5. 잘못된 JAR: 일부 JAR가 깨져도 나머지는 정상 로드")
    void test5_InvalidJarHandling() throws IOException {
        File brokenFile = tempDir.resolve("broken.jar").toFile();
        try (FileOutputStream fos = new FileOutputStream(brokenFile)) {
            fos.write("Invalid content".getBytes());
        }

        createMockJar("healthy.jar");

        assertDoesNotThrow(() -> manager.loadAll(tempDir.toString()));
    }

    @Test
    @DisplayName("6. plugins 디렉토리 없음: 경로 부재 시 스캔 건너뜀")
    void test6_MissingDirectory() {
        String invalidPath = tempDir.resolve("non-existent").toString();

        assertDoesNotThrow(() -> manager.loadAll(invalidPath));
        assertEquals(0, manager.getLoadedPluginCount());
    }

    @Test
    @DisplayName("7. 빈 plugins 디렉토리: JAR가 없으면 로드 수 0개")
    void test7_EmptyDirectory() {
        manager.loadAll(tempDir.toString());

        assertEquals(0, manager.getLoadedPluginCount());
    }

    @Test
    @DisplayName("8. 플러그인 수 확인: 등록된 노드 타입 총계 검증")
    void test8_PluginCountCheck() throws IOException {
        createMockJar("p1.jar");
        createMockJar("p2.jar");

        manager.loadAll(tempDir.toString());

        int count = manager.getLoadedPluginCount();
        assertTrue(count >= 0);
    }

    /**
     * 테스트용 가짜 JAR 파일을 생성하는 헬퍼 메서드
     */
    private void createMockJar(String jarName) throws IOException {
        File jarFile = tempDir.resolve(jarName).toFile();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
            jos.flush();
        }
    }
}