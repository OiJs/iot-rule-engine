package com.fbp.engine.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * 외부 JAR 파일들로부터 클래스를 동적으로 로드하는 전용 클래스로더입니다.
 * URLClassLoader를 상속받아 자원 해제 및 클래스 격리 기능을 제공합니다.
 */
public class PluginClassLoader extends URLClassLoader {

    /**
     * @param jarFiles 로드할 JAR 파일 목록
     * @param parent 부모 클래스로더 (일반적으로 엔진의 클래스로더)
     * @throws IOException JAR 파일을 URL로 변환하거나 존재하지 않을 때 발생
     */
    public PluginClassLoader(List<File> jarFiles, ClassLoader parent) throws IOException {
        super(toURLs(jarFiles), parent);
    }

    /**
     * 존재하지 않는 JAR 처리: 파일 존재 여부를 검증하고 URL 배열로 변환합니다.
     */
    private static URL[] toURLs(List<File> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return new URL[0];
        }

        URL[] urls = new URL[files.size()];
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);

            if (!file.exists()) {
                throw new FileNotFoundException("JAR 파일을 찾을 수 없습니다: " + file.getAbsolutePath());
            }
            if (!file.canRead()) {
                throw new IOException("JAR 파일을 읽을 수 있는 권한이 없습니다: " + file.getName());
            }

            urls[i] = file.toURI().toURL();
        }
        return urls;
    }

    /**
     * 리소스 해제: close() 호출 시 모든 JAR 파일의 핸들을 해제합니다.
     * URLClassLoader의 close()는 열려있는 모든 JAR 파일 리소스를 닫습니다.
     */
    @Override
    public void close() throws IOException {
        super.close();
    }
}