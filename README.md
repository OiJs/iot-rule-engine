# FBP IoT Rule Engine: Distributed Architecture

본 프로젝트는 **Flow-Based Programming (FBP)** 패러다임을 기반으로 설계된 IoT 룰 엔진입니다. 복잡한 데이터 처리 흐름을 독립적인 노드(Node)들의 그래프로 추상화하며, 특히 고성능 비동기 메트릭 수집과 분산 환경을 위한 MQTT 브릿지 구조를 핵심 아키텍처로 채택하고 있습니다.

---

## 1. 핵심 아키텍처 설계 원칙

### 노드 독립성 (Node Isolation)
모든 노드는 자신이 어떤 노드와 연결되어 있는지, 혹은 자신의 메시지가 로컬 큐를 타는지 MQTT 브로커를 타는지 알지 못합니다. 오직 자신에게 정의된 **InputPort**에서 데이터를 받고 **OutputPort**로 데이터를 내보낼 뿐입니다. 이를 통해 노드의 재사용성을 극대화하고 런타임에 토폴로지를 자유롭게 변경할 수 있습니다.

### Hot/Cold Path 분리
성능 최적화를 위해 엔진의 데이터 흐름을 두 가지 경로로 엄격히 분리했습니다.
*   **Hot Path (메시지 처리)**: 실제 비즈니스 로직이 수행되는 경로입니다. 지연을 최소화하기 위해 모든 계측(Metric Tapping)은 Lock-free 큐에 이벤트를 던지는 것으로 끝납니다. 관련 포인트는 [AbstractNode](src/main/java/com/fbp/engine/node/AbstractNode.java)의 `process` 메서드에서 확인할 수 있습니다.
*   **Cold Path (집계 및 적재)**: 백그라운드 스레드에서 큐를 소비하며 통계를 집계하고 DB(InfluxDB)에 기록합니다. 이 과정에서 발생하는 부하가 메인 플로우 처리에 영향을 주지 않습니다. 관련 로직은 [MetricsCollector](src/main/java/com/fbp/engine/metrics/MetricsCollector.java)와 [MetricsAggregator](src/main/java/com/fbp/engine/metrics/MetricsAggregator.java)에서 확인 가능합니다.

---

## 2. 주요 컴포넌트 설계 상세

### 2.1. Node & AbstractNode (추상화 레이어)
*   **[Node (Interface)](src/main/java/com/fbp/engine/core/Node.java)**: 메시지 처리를 위한 `process(Message)`와 생명주기 메서드(`initialize`, `shutdown`)를 정의합니다.
*   **[AbstractNode (Base Class)](src/main/java/com/fbp/engine/node/AbstractNode.java)**: 모든 노드의 공통 기능을 구현합니다.
    *   **Port Management**: 내부적으로 `Map<String, InputPort/OutputPort>`를 관리하여 포트 기반 통신을 지원합니다.
    *   **Metric Tapping**: `process` 메서드 실행 전후로 시간을 측정하고 성공/실패 여부를 비동기로 보고합니다.
    *   **Error Handling**: 처리 중 예외 발생 시 [ErrorPort](src/main/java/com/fbp/engine/core/ErrorPort.java)로 에러 메시지를 자동 전송하여 플로우 전체의 안정성을 보장합니다.

### 2.2. Flow & FlowEngine (실행 레이어)
*   **[Flow](src/main/java/com/fbp/engine/core/Flow.java)**: 노드들과 그 사이를 잇는 `Connection`들의 집합인 토폴로지 객체입니다.
    *   **Validation**: 배포 전 DFS(깊이 우선 탐색) 알고리즘을 사용하여 그래프 내의 **순환 참조(Cycle)를 감지**하고 유효성을 검증합니다.
*   **[FlowEngine](src/main/java/com/fbp/engine/core/FlowEngine.java)**: 실제 런타임을 운영합니다.
    *   **Worker Pool**: 각 `Connection`의 `poll()` 메서드를 실행하는 독립적인 워커 스레드들을 관리합니다.
    *   **Resource Management**: 플로우의 시작/중지 시 스레드 할당 및 해제를 총괄합니다.

### 2.3. FlowManager & Parser (관리 레이어)
*   **[FlowManager](src/main/java/com/fbp/engine/engine/FlowManager.java)**: 플로우의 전체 생명주기를 관장하는 Facade 클래스입니다.
    *   **Assembly**: [NodeRegistry](src/main/java/com/fbp/engine/registry/NodeRegistry.java)와 [ConnectionFactory](src/main/java/com/fbp/engine/core/ConnectionFactory.java)를 사용하여 설계도(JSON)로부터 실제 객체 그래프를 조립합니다.
    *   **Hot Patch**: 실행 중인 플로우의 설정을 실시간으로 변경합니다. 이전 설계도와 새 설계도의 차이점(Diff)을 계산하여 선별적으로 업데이트합니다.
*   **[JsonFlowParser](src/main/java/com/fbp/engine/parser/JsonFlowParser.java)**: 복잡한 JSON 구조를 엔진이 이해할 수 있는 [FlowDefinition](src/main/java/com/fbp/engine/parser/FlowDefinition.java) 레코드 구조로 변환합니다.

### 2.4. Connection (전송 계층 추상화)
`Strategy 패턴`을 적용하여 [Connection](src/main/java/com/fbp/engine/core/Connection.java) 인터페이스로 메시지 전달 방식을 추상화했습니다.
*   **[LocalConnection](src/main/java/com/fbp/engine/core/LocalConnection.java)**: JVM 내부의 `BlockingQueue`를 사용하여 고속 통신을 지원합니다.
*   **[MqttBridgeConnection](src/main/java/com/fbp/engine/core/MqttBridgeConnection.java)**: 외부 MQTT 브로커를 경유하여 **분산 FBP 구성**을 가능하게 합니다. 공유 연결 관리는 [MqttPool](src/main/java/com/fbp/engine/core/MqttPool.java)이 담당합니다.

---

## 3. 주요 클래스별 구현 상세 (Implementation Deep Dive)

### 3.1. [AbstractNode](src/main/java/com/fbp/engine/node/AbstractNode.java) (기본 노드 프레임워크)
모든 비즈니스 로직 노드의 부모 클래스로, **템플릿 메서드 패턴**을 통해 메시지 처리 라이프사이클을 제어합니다.
*   **`process(Message)`**: 메시지 수신의 엔트리 포인트입니다. 실제 비즈니스 로직인 `onProcess`를 호출하기 전후로 `System.nanoTime()`을 이용해 실행 시간을 마이크로초 단위로 측정하며, 성공 여부와 함께 `NodeProcessEvent`를 발행합니다.
*   **포트 관리**: `HashMap`을 통해 입력/출력 포트를 관리하며, 하위 클래스에서 `addInputPort`, `addOutputPort`를 통해 동적으로 인터페이스를 구성할 수 있습니다.
*   **에러 격리**: 처리 중 예외 발생 시 플로우 전체가 중단되지 않도록 `handleNodeError`가 가로채어 에러 전용 메시지를 생성하고 연결된 `ErrorPort`로 우회시킵니다.

### 3.2. [Flow](src/main/java/com/fbp/engine/core/Flow.java) (토폴로지 관리자)
노드와 연결의 그래프 구조를 유지하며 자가 검증 기능을 수행합니다.
*   **순환 참조 감지**: `validate()` 메서드 실행 시 **DFS 기반의 3-Color링 알고리즘**을 사용하여 그래프 내의 데드락 위험(순환 구조)을 사전에 차단합니다.
*   **동적 조작**: 런타임에 노드를 추가하거나 특정 연결을 끊는 `removeConnection` 등의 기능을 제공합니다. 특히 연결 제거 시 큐에 남은 메시지가 안전하게 소비될 때까지 대기하는 **Drain 로직**이 구현되어 있습니다.

### 3.3. [FlowEngine](src/main/java/com/fbp/engine/core/FlowEngine.java) (실행 런타임)
실제 메시지를 나르는 워커(Worker) 스레드를 운영하는 물리적 엔진입니다.
*   **워커 루프**: 각 `Connection`마다 독립적인 워커 루프를 스레드 풀에 할당합니다. 이 워커는 `conn.poll()`에서 메시지가 나올 때까지 대기하다가, 데이터가 들어오면 즉시 타겟 노드의 `receive()`를 호출합니다.
*   **중앙 집중 계측**: 엔진 생성 시 `MetricsAggregator`와 `MetricsScheduler`를 함께 가동하여, 엔진 내에서 발생하는 모든 활동을 실시간으로 추적합니다.

### 3.4. [FlowManager](src/main/java/com/fbp/engine/engine/FlowManager.java) (오케스트레이터)
사용자의 명령을 엔진이 이해할 수 있는 동작으로 변환하는 최고위 레이어입니다.
*   **Hot Patch 알고리즘**: 가장 복잡한 로직 중 하나로, 기존 설계도와 새로운 설계도의 노드 ID, 설정을 비교하여 **최소한의 변경**만 적용합니다. 예를 들어 노드 A와 B 사이의 연결만 바뀌었다면, 노드 자체를 재시작하지 않고 중간의 `Connection` 객체만 교체하여 무중단 업데이트를 실현합니다.
*   **객체 조립**: `NodeRegistry`를 통해 문자열 타입명으로부터 실제 클래스 인스턴스를 동적으로 생성합니다.

### 3.5. [MetricsCollector](src/main/java/com/fbp/engine/metrics/MetricsCollector.java) & [MetricsAggregator](src/main/java/com/fbp/engine/metrics/MetricsAggregator.java) (메트릭 엔진)
수천 건의 메시지 처리 속도를 저하시키지 않으면서 정밀한 통계를 내기 위한 구조입니다.
*   **비동기 완충**: `LinkedBlockingQueue`를 이용해 Hot Path에서 던지는 이벤트를 초당 수만 건까지 수용합니다.
*   **P99 백분위수**: 단순 평균은 병목 지점을 찾기에 부족합니다. `HdrHistogram`을 이용해 전체 지연 시간의 분포를 계산하여, 하위 99%의 처리 속도(P99)를 가시화합니다.

### 3.6. [MqttBridgeConnection](src/main/java/com/fbp/engine/core/MqttBridgeConnection.java) (분산 커넥터)
물리적으로 떨어진 노드들을 하나로 묶는 브릿지 역할을 수행합니다.
*   **공유 클라이언트**: 각 연결마다 MQTT 클라이언트를 만들면 오버헤드가 크므로, [MqttPool](src/main/java/com/fbp/engine/core/MqttPool.java)을 통해 하나의 브로커 주소당 하나의 비동기 클라이언트를 공유합니다.
*   **지능형 토픽 라우팅**: `MqttPool`에 글로벌 메시지 콜백을 설정하고, 수신된 메시지를 토픽별 핸들러 맵을 통해 각 연결 객체의 내부 큐로 정확히 배달합니다. 이는 Paho v5의 `subscribe` 리스너 배열 버그를 피하기 위한 설계이기도 합니다.

### 3.7. [BackpressureConnection](src/main/java/com/fbp/engine/core/BackpressureConnection.java) (흐름 제어 커넥터)
데이터 생산 속도가 소비 속도를 앞지를 때 발생하는 시스템 과부하를 관리합니다.
*   **전략 패턴(Strategy Pattern) 적용**: [BackpressureStrategy](src/main/java/com/fbp/engine/core/BackpressureStrategy.java) 인터페이스를 통해 상황에 맞는 부하 처리 정책을 주입할 수 있습니다.
*   **[DropOldestStrategy](src/main/java/com/fbp/engine/core/DropOldestStrategy.java)**: 실시간성이 중요한 경우, 큐가 가득 차면 가장 오래된 데이터를 버리고 최신 데이터를 수용합니다.
*   **[DropNewestStrategy](src/main/java/com/fbp/engine/core/DropNewestStrategy.java)**: 데이터 일관성이 중요한 경우, 큐가 가득 차면 새로 들어오는 데이터를 무시합니다.
*   **가시성 연동**: 데이터 드롭 발생 시 즉시 `MetricsCollector`에 보고하여 CLI에서 유실 현황을 실시간 모니터링할 수 있게 합니다.

---

## 4. 메트릭 수집 및 모니터링 시스템


엔진의 안정성과 가시성을 확보하기 위해 설계된 다층적 메트릭 시스템입니다. 고성능 처리를 위해 비동기 집계 및 적재 구조를 채택하고 있습니다.

### 3.1. 데이터 모델 및 수집 흐름
*   **[MetricEvent](src/main/java/com/fbp/engine/metrics/event/MetricEvent.java)**: 모든 메트릭 데이터를 추상화한 `sealed interface`입니다.
    *   **NodeProcessEvent**: 노드별 처리 시간, 성공 여부, 입출력 데이터량 정보를 담습니다.
    *   **WireDeliverEvent**: 연결(Wire)별 메시지 전달 건수, 큐 적체량, 드롭 발생 여부를 기록합니다.
    *   **DomainExtractionEvent**: 페이로드에서 도메인 데이터(센서값 등)를 추출하기 위한 원천 메시지 이벤트입니다.
    *   **FlowEvent**: 플로우의 배포, 시작, 중지 등 생명주기 변화 이벤트를 관리합니다.

### 3.2. 핵심 컴포넌트 상세
*   **[MetricsCollector](src/main/java/com/fbp/engine/metrics/MetricsCollector.java) (The Buffer)**
    *   **Hot Path 보호**: 메시지 처리 스레드가 블로킹되지 않도록 `LinkedBlockingQueue`를 사용하여 이벤트를 비동기로 수집합니다.
    *   **Drop Strategy**: 시스템 부하가 임계치를 넘을 경우, 메시지 처리를 우선하기 위해 메트릭 이벤트를 자동으로 드롭(Drop)하여 엔진의 성능 저하를 원천 차단합니다.
*   **[MetricsAggregator](src/main/java/com/fbp/engine/metrics/MetricsAggregator.java) (The Aggregator)**
    *   **O(1) 집계**: `LongAdder`를 사용하여 멀티스레드 환경에서의 카운터 경합을 최소화합니다.
    *   **정밀한 지연 시간 측정**: **HdrHistogram** 라이브러리를 내장하여, 단순 평균값이 아닌 **P99(99퍼센타일)** 지연 시간을 매우 낮은 메모리 오버헤드로 실시간 계산합니다.
*   **도메인 데이터 처리 유틸리티**
    *   **[DomainMetricsExtractor](src/main/java/com/fbp/engine/metrics/DomainMetricsExtractor.java)**: 복잡한 JSON 페이로드에서 JSON Pointer 기술을 사용하여 원하는 필드값을 동적으로 추출합니다.
    *   **[TimeWindowBucketer](src/main/java/com/fbp/engine/metrics/TimeWindowBucketer.java)**: 추출된 센서 데이터를 기반으로 **1분/1시간/1일 단위의 텀블링 윈도우** 통계(평균, 최소, 최대)를 관리합니다.
*   **데이터 적재 엔진**
    *   **[InfluxBatchWriter](src/main/java/com/fbp/engine/metrics/InfluxBatchWriter.java)**: InfluxDB 2.0 API를 사용하여 배치 쓰기 및 자동 재시도 로직을 수행합니다.
    *   **[MetricsScheduler](src/main/java/com/fbp/engine/metrics/MetricsScheduler.java)**: 10초마다 현재 메모리상의 모든 통계 스냅샷을 캡처하여 InfluxDB Line Protocol 형태로 발행합니다.

### 3.3. 수집 지표 및 Measurement
1.  **engine_stats**: 호스트별 활성 플로우 수, 총 노드 수, JVM 힙 메모리 사용량.
2.  **flow_stats**: 플로우별 누적 처리량 및 에러율.
3.  **node_stats**: 노드별 입출력 바이트, 처리 지연 시간(Avg/P99).
4.  **wire_stats**: 연결별 전달 건수, 현재 큐 적체량, 드롭 건수.
5.  **sensor_raw**: 개별 메시지 발생 시점의 원천 센서 데이터.
6.  **sensor_stats_1m/1h/1d**: 시간 윈도우별 집계 결과.
7.  **flow_events**: 운영 이력(누가, 언제, 어떤 변경을 했는지).

### 데이터 흐름
`Event Generation` → `[MetricsCollector](src/main/java/com/fbp/engine/metrics/MetricsCollector.java)` → `[MetricsAggregator](src/main/java/com/fbp/engine/metrics/MetricsAggregator.java)` → `[InfluxBatchWriter](src/main/java/com/fbp/engine/metrics/InfluxBatchWriter.java)`


---

## 4. 운영 인터페이스 (CLI)

**[FbpCli](src/main/java/com/fbp/engine/cli/FbpCli.java)** 클래스를 통해 다음과 같은 강력한 운영 기능을 지원합니다.

*   **Flow Control**: 배포, 시작, 중지, 삭제 및 동적 패치 적용.
*   **Live Monitoring**: `monitor flow <id>` 명령어를 통해 메시지 페이로드를 실시간으로 추적합니다.
*   **Stats Query**: 메트릭 집계 데이터를 즉시 조회합니다.

---

## 5. 기술 스택 및 라이브러리
*   **언어**: Java 21
*   **통신**: [Eclipse Paho MQTT v5](src/main/java/com/fbp/engine/core/MqttPool.java) (Async Client)
*   **데이터 처리**: Jackson (JSON/YAML)
*   **집계**: [HdrHistogram](src/main/java/com/fbp/engine/metrics/MetricsAggregator.java) (지연 시간 통계)
*   **저장소**: [InfluxDB 2.0](src/main/java/com/fbp/engine/metrics/InfluxBatchWriter.java) (시계열 데이터)
