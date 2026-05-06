package com.fbp.engine.plugin;

import com.fbp.engine.registry.NodeRegistry;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PluginScanner와 PluginClassLoader를 조율하여 시스템 확장을 관리합니다.
 */
@Slf4j
public class PluginManager implements AutoCloseable {
    private final NodeRegistry registry;
    private final PluginScanner scanner;

    private final Map<String, NodeDescriptor> loadedPlugins = new ConcurrentHashMap<>();

    private final List<PluginClassLoader> activeLoaders = new ArrayList<>();

    public PluginManager(NodeRegistry registry) {
        this.registry = registry;
        this.scanner = new PluginScanner();
    }

    /**
     * 클래스패스와 외부 경로의 모든 플러그인을 로드합니다.
     */
    public void loadAll(String externalPath) {
        log.info("내장 플러그인 스캔 중...");
        process(scanner.scanClassPath());

        log.info("외부 플러그인 탐색: {}", externalPath);
        List<File> jars = scanner.findJars(externalPath);

        if (jars.isEmpty()) {
            log.info("로드할 외부 JAR 파일이 없습니다.");
            return;
        }

        try {
            // List<File>을 한 번에 넘겨 효율적으로 로더를 생성합니다.
            PluginClassLoader loader = new PluginClassLoader(jars, getClass().getClassLoader());

            // 로더가 닫히지 않도록 리스트에 보관 (이걸 안 하면 런타임에 NoClassDefFoundError 발생)
            activeLoaders.add(loader);

            log.info("{} 개의 외부 JAR 로딩 시도...", jars.size());
            process(scanner.scanWithLoader(loader));

        } catch (IOException e) {
            log.error("외부 플러그인 클래스로더 생성 실패 (파일 없음 또는 권한 에러): {}", e.getMessage());
        } catch (Exception e) {
            log.error("외부 플러그인 로드 중 예상치 못한 에러", e);
        }
    }

    /**
     * ServiceLoader가 찾은 모든 Provider를 안전하게 순회하며 등록합니다.
     */
    private void process(ServiceLoader<NodeProvider> loader) {
        for (NodeProvider provider : loader) {
            try {
                // 개별 플러그인 예외 격리 (Fail-Safe)
                registerFromProvider(provider);
            } catch (PluginException e) {
                log.error("플러그인 정의 오류: {}", e.getMessage());
            } catch (Exception e) {
                log.error("플러그인 실행 중 예상치 못한 예외: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Provider로부터 기술서를 추출하여 Registry에 등록합니다.
     */
    private void registerFromProvider(NodeProvider provider) {
        List<NodeDescriptor> descriptors = provider.getNodeDescriptors();

        if (descriptors == null || descriptors.isEmpty()) {
            throw new PluginException("Provider가 빈 노드 목록을 반환함: " + provider.getClass().getName());
        }

        for (NodeDescriptor descriptor : descriptors) {
            validate(descriptor);

            // [충돌 처리 정책] 기존 등록된 타입은 보호함
            if (!registry.isRegistered(descriptor.typeName())) {
                registry.register(descriptor.typeName(), descriptor.factory());
                loadedPlugins.put(descriptor.typeName(), descriptor);
                log.info("플러그인 노드 등록 성공: [{}]", descriptor.typeName());
            } else {
                log.warn("이미 등록된 노드 타입이라 무시합니다: {}", descriptor.typeName());
            }
        }
    }

    private void validate(NodeDescriptor descriptor) {
        if (descriptor.typeName() == null || descriptor.typeName().isBlank()) {
            throw new PluginException("Node typeName이 누락되었습니다.");
        }
        if (descriptor.factory() == null) {
            throw new PluginException("Node factory가 누락되었습니다: " + descriptor.typeName());
        }
    }

    public int getLoadedPluginCount() {
        return loadedPlugins.size();
    }

    @Override
    public void close() {
        for (PluginClassLoader loader : activeLoaders) {
            try {
                loader.close();
                log.info("PluginClassLoader 리소스 해제 완료.");
            } catch (IOException e) {
                log.error("클래스로더 종료 중 오류", e);
            }
        }
        activeLoaders.clear();
    }
}