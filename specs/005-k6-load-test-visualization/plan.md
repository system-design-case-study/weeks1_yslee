# Implementation Plan: k6 부하 테스트 + Prometheus/Grafana 시각화

**Branch**: `005-k6-load-test-visualization` | **Date**: 2026-02-09 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-k6-load-test-visualization/spec.md`

## Summary

Phase 1~4에서 구현한 Proximity Service MVP에 k6 부하 테스트, Prometheus 메트릭 수집, Grafana 시각화를 추가한다. k6로 실제 HTTP 부하를 발생시키고, Prometheus가 앱 + k6 양쪽 메트릭을 수집하며, Grafana 대시보드에서 실시간으로 성능을 시각화한다. 전체 스택은 Docker Compose로 원클릭 실행한다.

## Technical Context

**Language/Version**: Java 21 (LTS) + k6 (JavaScript ES6 스크립트)
**Primary Dependencies**: Spring Boot 3.4.1, Spring Boot Actuator, Micrometer Prometheus Registry
**Infrastructure**: Docker Compose (기존 MySQL 8.0 + Redis 7 + 신규 Prometheus v2.54.1 + Grafana 11.4.0 + k6 0.55.0)
**Storage**: Prometheus TSDB (시계열 메트릭 저장)
**Testing**: k6 부하 테스트 (JUnit 아님)
**Target Platform**: 로컬 Docker 환경
**Performance Goals**: 주변 검색 p95 ≤ 500ms (50 VU), CRUD 에러율 < 1%
**Constraints**: 비즈니스 로직 변경 없음, 단일 머신 부하 테스트
**Scale/Scope**: 1,000건 시딩 데이터, 최대 50 VU

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. ADR-Driven Design | PASS | 기술 선택(k6/Prometheus/Grafana)의 근거를 research.md에 기록 |
| II. Simplicity First | PASS | 가장 단순한 구성: Docker Compose + 프로비저닝 파일. CDC나 별도 메시지 큐 없음 |
| III. Data Locality | PASS | 기존 데이터 모델 변경 없음. Prometheus는 메트릭 전용 저장소 |
| IV. Read/Write Separation | PASS | 모니터링 스택은 기존 서비스 아키텍처에 영향 없음 |
| V. Study-First Implementation | PASS | k6/Prometheus/Grafana 학습이 핵심 목적. 용량 추정보다 도구 활용 학습에 초점 |

**Post-Phase 1 재검증**: 모든 원칙 여전히 충족. 프로덕션 코드 변경은 Actuator 의존성 + application.yml 설정에 한정.

## Project Structure

### Documentation (this feature)

```text
specs/005-k6-load-test-visualization/
├── spec.md
├── plan.md              # This file
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── api-endpoints.md
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit.tasks로 생성
```

### Source Code (신규/변경 파일)

```text
# 프로덕션 코드 변경 (최소)
build.gradle.kts                              # actuator + micrometer 의존성 추가
src/main/resources/application.yml            # management.endpoints 설정 추가

# Docker Compose 인프라
docker-compose.yml                            # prometheus, grafana, k6 서비스 추가

# Prometheus 설정
monitoring/prometheus/prometheus.yml           # scrape config + remote write 수신

# Grafana 프로비저닝
monitoring/grafana/provisioning/
  datasources/datasource.yml                  # Prometheus 데이터소스 자동 등록
  dashboards/dashboard-provider.yml           # 대시보드 JSON 로더 설정
  dashboards/json/
    proximity-service-dashboard.json          # 앱 메트릭 대시보드
    k6-load-test-dashboard.json               # k6 메트릭 대시보드

# k6 부하 테스트 스크립트
k6/scripts/
  helpers/seed-data.js                        # 테스트 데이터 시딩 헬퍼
  nearby-search.js                            # 주변 검색 부하 테스트
  crud-mixed.js                               # CRUD 혼합 부하 테스트
```

**Structure Decision**: 기존 프로젝트 루트에 `monitoring/` (Prometheus/Grafana 설정)과 `k6/` (부하 테스트 스크립트) 디렉토리를 추가. 프로덕션 소스 구조는 유지.

## Implementation Strategy

### Step 1: Spring Boot Actuator + Micrometer 설정

1. `build.gradle.kts`에 의존성 추가:
   - `implementation("org.springframework.boot:spring-boot-starter-actuator")`
   - `runtimeOnly("io.micrometer:micrometer-registry-prometheus")`

2. `application.yml`에 메트릭 엔드포인트 설정:
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health, info, metrics, prometheus
     endpoint:
       prometheus:
         enabled: true
     metrics:
       tags:
         application: proximity-service
   ```

3. 검증: `GET /actuator/prometheus` → Prometheus 텍스트 형식 메트릭 반환

### Step 2: Docker Compose 모니터링 스택

1. `docker-compose.yml`에 서비스 추가:
   - `prometheus`: v2.54.1, `--web.enable-remote-write-receiver` 플래그
   - `grafana`: 11.4.0, 프로비저닝 볼륨 마운트, 익명 접근 허용
   - `k6`: 0.55.0, `profiles: [test]`로 분리, Prometheus RW URL 환경변수

2. 앱 서비스에 네트워크 추가 (Prometheus가 스크랩 가능하도록)

### Step 3: Prometheus 설정

1. `monitoring/prometheus/prometheus.yml`:
   - `scrape_configs`: spring-boot-app (app:8080, 5초 간격)
   - Remote write receiver 활성화 (k6 메트릭 수신)

### Step 4: Grafana 프로비저닝

1. 데이터소스: Prometheus (`http://prometheus:9090`)
2. 대시보드 프로바이더: JSON 파일 자동 로드
3. 대시보드 JSON 2개:
   - 앱 메트릭: HTTP 레이턴시, RPS, 에러율, JVM, HikariCP
   - k6 메트릭: VU 수, 요청 레이턴시, 처리량

### Step 5: k6 부하 테스트 스크립트

1. `helpers/seed-data.js`: 시딩 헬퍼 (1,000건 사업장 등록)
2. `nearby-search.js`: 주변 검색 시나리오 (ramp-up → steady → ramp-down)
3. `crud-mixed.js`: CRUD 혼합 시나리오 (비율: 검색60/조회20/생성10/수정5/삭제5)

### Step 6: 통합 테스트 & 문서

1. `docker compose up -d` → 전체 스택 시작 확인
2. k6 부하 테스트 실행 → Grafana 대시보드 실시간 확인
3. quickstart.md 검증

## Key Decisions

### D-001: k6 Prometheus Remote Write (push) 방식 선택

**Decision**: k6 built-in `--out experimental-prometheus-rw` 사용

**Rationale**: k6 코어에 내장된 기능으로 추가 서비스 불필요. StatsD + statsd_exporter 방식 대비 구성이 단순하고 Grafana Labs 공식 권장 방식. [R-001 참조]

### D-002: 기존 docker-compose.yml 확장 vs 별도 파일

**Decision**: 기존 `docker-compose.yml`에 서비스 추가

**Rationale**: 단일 `docker compose up`으로 전체 스택 시작 가능. 별도 파일(`docker-compose.monitoring.yml`) 분리 시 `-f` 플래그 필요하고 네트워크 공유 설정이 복잡해짐.

### D-003: k6 profiles 분리

**Decision**: k6 서비스에 `profiles: [test]` 적용

**Rationale**: `docker compose up`은 앱 + DB + 모니터링만 시작. 부하 테스트는 명시적으로 `--profile test run --rm k6`로 실행. 불필요한 부하 테스트 자동 실행 방지.

## Complexity Tracking

> 위반 사항 없음. Constitution 원칙 모두 충족.
