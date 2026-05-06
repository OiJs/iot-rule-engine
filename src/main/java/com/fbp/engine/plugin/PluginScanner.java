package com.fbp.engine.plugin;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public class PluginScanner {
    
    // 1. 클래스패스 스캔
    public ServiceLoader<NodeProvider> scanClassPath() {
        return ServiceLoader.load(NodeProvider.class);
    }

    // 2. 외부 디렉토리에서 JAR 파일 목록 추출
    public List<File> findJars(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();

        File[] files = dir.listFiles((d, name) -> name.endsWith(".jar"));
        return (files == null) ? Collections.emptyList() : Arrays.asList(files);
    }

    // 3. 특정 클래스로더를 통한 서비스 탐색
    public ServiceLoader<NodeProvider> scanWithLoader(ClassLoader loader) {
        return ServiceLoader.load(NodeProvider.class, loader);
    }
}