package com.fbp.engine.node.io;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.AbstractNode;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//TODO 재연결 시 데이터 유실 고민, 연결 실패 에러포트 or Event Bus / Callback 고려, config 정보 properties고려
public abstract class ProtocolNode extends AbstractNode {
    public enum ConnectionState{
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }


    private final Map<String, Object> config;
    protected volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    protected long reconnectIntervalMs = 5000;

    private ScheduledExecutorService reconnectScheduler;
    private int maxReconnectAttempts = 10;
    private int currentReconnectCount = 0;

    public ProtocolNode(String id, Map<String, Object> config) {
        super(id);
        this.config = config;
        if(config.containsKey("reconnectIntervalMs")) {
            this.reconnectIntervalMs = ((Number) config.get("reconnectIntervalMs")).longValue();
        }
        if (config.containsKey("maxReconnectAttempts")) {
            this.maxReconnectAttempts = ((Number) config.get("maxReconnectAttempts")).intValue();
        }

    }

    @Override
    public void initialize() {
        attemptConnection();
    }

    private synchronized void attemptConnection() {
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
        if(currentReconnectCount >= maxReconnectAttempts) {
            System.err.println("[" + getId() + "] 최대 재연결 시도 횟수(" + maxReconnectAttempts + ") 초과. 연결 실패");
            return;
        }

        if(reconnectScheduler == null || reconnectScheduler.isShutdown()) {
            reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        currentReconnectCount++;
        System.out.println("[" + getId() + "] " + reconnectIntervalMs + "ms 후 재연결을 시도합니다. (" + currentReconnectCount + "/" + maxReconnectAttempts + ")");
        reconnectScheduler.schedule(this::attemptConnection, reconnectIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        System.out.println("[" + getId() + "] 노드 종료 및 자원 해제 중...");

        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            reconnectScheduler.shutdownNow();
        }

        try {
            disconnect();
        } catch (Exception e) {
            System.err.println("[" + getId() + "] 연결 해제 중 오류 발생: " + e.getMessage());
        } finally {
            connectionState = ConnectionState.DISCONNECTED;
            System.out.println("[" + getId() + "] 연결이 안전하게 종료되었습니다.");
        }
    }

    @Override
    protected void onProcess(Message message) {

    }

    protected abstract void connect() throws Exception;
    protected abstract void disconnect() throws Exception;

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public Object getConfig(String key) {
        return config.get(key);
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }
}
