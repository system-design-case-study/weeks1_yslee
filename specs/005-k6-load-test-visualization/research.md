# Research: k6 부하 테스트 + Prometheus/Grafana 시각화

## R-001: k6 → Prometheus 메트릭 전송 방식

**Decision**: k6 built-in Prometheus Remote Write 출력 사용

**Rationale**:
- k6 v0.42.0부터 Prometheus Remote Write가 코어에 내장됨 (xk6 확장 불필요).
- k6에서 Prometheus로 직접 push하므로 중간 서비스가 필요 없음.
- Grafana Labs 공식 권장 방식.
- 환경변수 `K6_PROMETHEUS_RW_SERVER_URL`과 `--out experimental-prometheus-rw` 플래그로 설정.
- Prometheus에 `--web.enable-remote-write-receiver` 플래그 필수.

**Alternatives considered**:
- **StatsD + statsd_exporter**: 추가 컨테이너(statsd_exporter)와 복잡한 매핑 설정 필요. 불필요한 복잡도.
- **k6 Cloud**: 유료 서비스. 로컬 실행 목적에 부적합.
- **InfluxDB + Grafana**: Prometheus 대비 추가 저장소. 이미 앱 메트릭을 Prometheus로 수집하므로 통일이 유리.

## R-002: Spring Boot Actuator + Micrometer Prometheus 설정

**Decision**: `spring-boot-starter-actuator` + `micrometer-registry-prometheus` 의존성 추가

**Rationale**:
- Spring Boot 3.4.x에서 두 의존성만 추가하면 `/actuator/prometheus` 엔드포인트 자동 노출.
- 별도 코드 없이 HTTP 요청, JVM, HikariCP, GC 메트릭이 자동 수집됨.
- `management.endpoints.web.exposure.include: prometheus`로 활성화.
- Prometheus scrape config에서 `metrics_path: '/actuator/prometheus'`로 수집.

**Alternatives considered**:
- **수동 메트릭 노출**: MeterRegistry 직접 사용. 불필요한 코드 작성.
- **OpenTelemetry Collector**: OTLP 프로토콜 기반. 학습 프로젝트에 과도한 복잡도.

## R-003: Grafana 대시보드 자동 프로비저닝

**Decision**: Docker 볼륨 마운트 + YAML/JSON 프로비저닝 파일

**Rationale**:
- Grafana Docker 이미지의 `/etc/grafana/provisioning/` 디렉토리에 설정 파일을 마운트하면 자동 구성.
- `datasources/datasource.yml`: Prometheus 데이터소스 자동 등록.
- `dashboards/dashboard-provider.yml` + JSON: 대시보드 자동 로드.
- 컨테이너 재시작해도 설정이 유지됨 (코드로 관리).

**Alternatives considered**:
- **Grafana API로 런타임 생성**: 컨테이너 재시작 시 설정 유실. 자동화에 부적합.
- **Grafana Terraform provider**: 학습 프로젝트에 과도함.

## R-004: Docker Compose 스택 구성 패턴

**Decision**: 기존 docker-compose.yml에 prometheus + grafana 서비스 추가, k6는 profiles로 분리

**Rationale**:
- 기존 MySQL + Redis 서비스에 prometheus, grafana를 같은 네트워크에 추가.
- k6는 `profiles: [test]`로 선언하여 `docker compose up`에서는 시작하지 않음.
- 부하 테스트 시 `docker compose --profile test run --rm k6`로 실행.
- 모든 서비스가 같은 bridge 네트워크에서 서비스명으로 통신.

**Alternatives considered**:
- **k6 호스트 설치**: Docker 환경과 분리되어 네트워크 설정 복잡. 이식성 떨어짐.
- **별도 docker-compose.monitoring.yml**: 파일 분리로 관리 복잡. `-f` 플래그 필요.

## R-005: 권장 이미지 버전

**Decision**: Prometheus v2.54.1, Grafana 11.4.0, k6 0.55.0

**Rationale**:
- Prometheus v2.x는 안정적. v3.0에서 `--enable-feature=remote-write-receiver` 플래그가 제거되고 `--web.enable-remote-write-receiver`로 변경됨. v2.54.1이 2026년 초 기준 안정 버전.
- Grafana 11.x는 LTS급 안정성. 프로비저닝 API 호환.
- k6 0.55.0은 Prometheus Remote Write 내장 버전. 최신 안정.
