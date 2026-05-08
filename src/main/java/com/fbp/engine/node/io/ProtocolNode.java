package com.fbp.engine.node.io;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class ProtocolNode extends AbstractNode {
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    protected volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    protected long reconnectIntervalMs = 5000;
    protected int maxReconnectAttempts = 10;

    private ScheduledExecutorService reconnectScheduler;
    private int currentReconnectCount = 0;

    public ProtocolNode(String id, Map<String, Object> config) {
        // 부모 생성자(AbstractNode)에게 설정을 넘겨주어 공통 관리하게 함
        super(id, config);
        // 초기 필드 동기화
        syncConnectionConfig(config);
    }

    /**
     * [Hot-Reload] 설정이 변경되었을 때 호출되는 브릿지 메서드
     */
    @Override
    protected void onConfigUpdate(Map<String, Object> newConfig) {
        syncConnectionConfig(newConfig);

        // 만약 실행 중에 연결 대상(URL, Host 등)이 바뀌었다면 재연결 필요
        if (newConfig.containsKey("url")) {
            shutdown(); initialize();
        }

        System.out.println("[" + getId() + "] 연결 파라미터 업데이트 완료");
    }

    private void syncConnectionConfig(Map<String, Object> cfg) {
        if (cfg.containsKey("reconnectIntervalMs")) {
            this.reconnectIntervalMs = ((Number) cfg.get("reconnectIntervalMs")).longValue();
        }
        if (cfg.containsKey("maxReconnectAttempts")) {
            this.maxReconnectAttempts = ((Number) cfg.get("maxReconnectAttempts")).intValue();
        }
    }

    @Override
    public void initialize() {
        currentReconnectCount = 0;
        attemptConnection();
    }

    private synchronized void attemptConnection() {
        if (connectionState == ConnectionState.CONNECTED) return;

        connectionState = ConnectionState.CONNECTING;
        try {
            connect();
            connectionState = ConnectionState.CONNECTED;
            currentReconnectCount = 0;
            System.out.println("[" + getId() + "] 외부 시스템 연결 성공");
        } catch (Exception e) {
            connectionState = ConnectionState.ERROR;
            System.err.println("[" + getId() + "] 연결 실패: " + e.getMessage());
            reconnect();
        }
    }

    protected synchronized void reconnect() {
        if (currentReconnectCount >= maxReconnectAttempts) {
            System.err.println("[" + getId() + "] 최대 재연결 시도 횟수 초과 (" + maxReconnectAttempts + ")");
            return;
        }

        if (reconnectScheduler == null || reconnectScheduler.isShutdown()) {
            reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "reconnector-" + getId());
                t.setDaemon(true);
                return t;
            });
        }

        currentReconnectCount++;
        System.out.println("[" + getId() + "] " + reconnectIntervalMs + "ms 후 재연결 시도 (" + currentReconnectCount + "/" + maxReconnectAttempts + ")");
        reconnectScheduler.schedule(this::attemptConnection, reconnectIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        System.out.println("[" + getId() + "] 자원 해제 중...");

        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            reconnectScheduler.shutdownNow();
        }

        try {
            disconnect();
        } catch (Exception e) {
            System.err.println("[" + getId() + "] 해제 중 오류: " + e.getMessage());
        } finally {
            connectionState = ConnectionState.DISCONNECTED;
            System.out.println("[" + getId() + "] 연결 종료.");
        }
    }

    @Override
    protected void onProcess(Message message) {}

    protected abstract void connect() throws Exception;
    protected abstract void disconnect() throws Exception;

    public ConnectionState getConnectionState() { return connectionState; }
    public boolean isConnected() { return connectionState == ConnectionState.CONNECTED; }
}