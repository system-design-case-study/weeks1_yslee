# Tasks: k6 부하 테스트 + Prometheus/Grafana 시각화

**Input**: Design documents from `/specs/005-k6-load-test-visualization/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 프로젝트 의존성 추가 및 디렉토리 구조 생성

- [x] T001 Add Actuator + Micrometer Prometheus dependencies in build.gradle.kts (`spring-boot-starter-actuator`, `micrometer-registry-prometheus`)
- [x] T002 [P] Create monitoring directory structure: monitoring/prometheus/, monitoring/grafana/provisioning/datasources/, monitoring/grafana/provisioning/dashboards/json/
- [x] T003 [P] Create k6 directory structure: k6/scripts/helpers/

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Actuator 메트릭 엔드포인트 + Docker Compose 모니터링 스택 — 모든 User Story의 기반

**⚠️ CRITICAL**: 이 Phase 완료 전 k6 스크립트/대시보드 작성 불가

- [x] T004 Configure management endpoints in src/main/resources/application.yml (management.endpoints.web.exposure.include: health,info,metrics,prometheus, management.metrics.tags.application: proximity-service)
- [x] T005 Create Prometheus config in monitoring/prometheus/prometheus.yml (scrape spring-boot-app at app:8080/actuator/prometheus 5s interval, self-monitoring at localhost:9090)
- [x] T006 [P] Create Grafana datasource provisioning in monitoring/grafana/provisioning/datasources/datasource.yml (Prometheus http://prometheus:9090, isDefault: true)
- [x] T007 [P] Create Grafana dashboard provider config in monitoring/grafana/provisioning/dashboards/dashboard-provider.yml (path: /etc/grafana/provisioning/dashboards/json)
- [x] T008 Add prometheus, grafana, k6 services to docker-compose.yml (prometheus v2.54.1 port 9090 with --web.enable-remote-write-receiver, grafana 11.4.0 port 3000 anonymous auth, k6 0.55.0 profiles:[test] with K6_PROMETHEUS_RW_SERVER_URL, shared network, provisioning volume mounts)

**Checkpoint**: `docker compose up -d` → http://localhost:9090 (Prometheus targets UP) + http://localhost:3000 (Grafana 접속) + `GET /actuator/prometheus` 메트릭 반환

---

## Phase 3: User Story 1 — k6 부하 테스트 실행 (Priority: P1) 🎯 MVP

**Goal**: k6 스크립트로 주변 검색 + CRUD API에 부하를 발생시키고 터미널에서 p50/p95/p99, RPS, 에러율 확인

**Independent Test**: `docker compose --profile test run --rm k6 run /scripts/nearby-search.js` → 터미널에 레이턴시/RPS/에러율 출력

### Implementation for User Story 1

- [x] T009 [US1] Create seed data helper in k6/scripts/helpers/seed-data.js (seedBusinesses(baseUrl, count) 함수, POST /v1/businesses/seed 호출, 서울 5대 랜드마크 좌표 상수)
- [x] T010 [US1] Create nearby search load test in k6/scripts/nearby-search.js (setup: 1000건 시딩, stages: ramp 10→50 VU steady 30s ramp-down, GET /v1/search/nearby 랜덤 좌표, thresholds p95<500ms error<1%)
- [x] T011 [P] [US1] Create CRUD mixed load test in k6/scripts/crud-mixed.js (setup: 500건 시딩, stages: ramp 5→20 VU steady 30s, 검색60%/조회20%/생성10%/수정5%/삭제5%, thresholds p95<1000ms error<1%)

**Checkpoint**: 두 k6 스크립트 에러 없이 실행, 터미널에 p95/RPS/에러율 출력. nearby-search p95 < 500ms, crud-mixed 에러율 < 1%

---

## Phase 4: User Story 2 — Prometheus 메트릭 수집 (Priority: P2)

**Goal**: k6 메트릭(Remote Write push) + 앱 메트릭(scrape pull)이 Prometheus에 시계열 저장

**Independent Test**: k6 실행 후 http://localhost:9090 에서 `http_server_requests_seconds_count`, `k6_http_req_duration_p95` 쿼리 성공

### Implementation for User Story 2

- [x] T012 [US2] Add K6_PROMETHEUS_RW_TREND_STATS env to k6 service in docker-compose.yml (p(95),p(99),min,max,avg) and verify --out experimental-prometheus-rw flag in k6 entrypoint
- [x] T013 [US2] Verify Prometheus scrape + Remote Write integration: run k6 test, query both app metrics and k6 metrics in Prometheus UI

**Checkpoint**: Prometheus UI에서 앱 메트릭(`http_server_requests_seconds_count`)과 k6 메트릭(`k6_http_req_duration_p95`) 모두 조회 가능

---

## Phase 5: User Story 3 — Grafana 대시보드 시각화 (Priority: P3)

**Goal**: 수집된 메트릭을 실시간 대시보드에서 그래프/차트로 시각화 (7개 패널)

**Independent Test**: http://localhost:3000 → 대시보드 선택 → 패널에 실시간 데이터 표시

### Implementation for User Story 3

- [x] T014 [P] [US3] Create app metrics dashboard in monitoring/grafana/provisioning/dashboards/json/proximity-service-dashboard.json (5 panels: HTTP latency p50/p95/p99 histogram_quantile, RPS rate, error rate, JVM heap, HikariCP connections)
- [x] T015 [P] [US3] Create k6 load test dashboard in monitoring/grafana/provisioning/dashboards/json/k6-load-test-dashboard.json (4 panels: VUs k6_vus, request latency p95/p99, RPS rate k6_http_reqs_total, error rate k6_http_req_failed)

**Checkpoint**: `docker compose up -d` → k6 테스트 실행 → Grafana에서 대시보드 2개, 총 9개 패널에 실시간 그래프 업데이트

---

## Phase 6: User Story 4 — 원클릭 통합 실행 (Priority: P4)

**Goal**: 단일 명령어로 전체 스택 시작, k6 테스트 실행 후 대시보드에서 실시간 확인

**Independent Test**: `docker compose up -d` → k6 run → Grafana 확인 → `docker compose down -v`

### Implementation for User Story 4

- [x] T016 [US4] Finalize docker-compose.yml healthchecks and startup order (app: /actuator/health, prometheus: /-/healthy, grafana: /api/health, depends_on chain: mysql→redis→app→prometheus→grafana, k6 depends_on app+prometheus)
- [x] T017 [US4] End-to-end integration validation: (1) docker compose up -d → all healthy, (2) k6 nearby-search → terminal + Grafana, (3) k6 crud-mixed → terminal + Grafana, (4) docker compose down -v → clean

**Checkpoint**: quickstart.md 전체 흐름 문제없이 동작

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 최종 정리 및 기존 테스트 영향 확인

- [x] T018 [P] Update .gitignore with monitoring data patterns (monitoring/grafana/data/, monitoring/prometheus/data/)
- [x] T019 Verify existing tests still pass after Actuator addition (./gradlew test — 87 tests, ./gradlew performanceTest — 11 tests)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 (T001) — BLOCKS all user stories
- **Phase 3 (US1)**: Depends on Phase 2 — k6 스크립트 작성 + 실행
- **Phase 4 (US2)**: Depends on Phase 3 — k6가 있어야 Remote Write 검증 가능
- **Phase 5 (US3)**: Depends on Phase 4 — 수집된 메트릭이 있어야 대시보드 검증 가능
- **Phase 6 (US4)**: Depends on Phase 3 + 4 + 5 — 전체 통합
- **Phase 7 (Polish)**: Depends on Phase 6

### User Story Dependencies

- **US1 (P1)**: Phase 2 완료 후 시작 — 독립 테스트 가능 (터미널 출력)
- **US2 (P2)**: US1 완료 후 — k6 스크립트 필요
- **US3 (P3)**: US2 완료 후 — Prometheus에 메트릭 필요
- **US4 (P4)**: US1~US3 모두 완료 후

### Parallel Opportunities

```text
Phase 1: T002 || T003 (디렉토리 생성)
Phase 2: T006 || T007 (Grafana provisioning 파일)
Phase 3: T010 완료 후 T011 병렬 가능 (T009 seed helper 먼저)
Phase 5: T014 || T015 (대시보드 JSON 2개)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: 의존성 + 디렉토리 (T001~T003)
2. Phase 2: Actuator + Docker Compose 스택 (T004~T008)
3. Phase 3: k6 스크립트 (T009~T011)
4. **STOP and VALIDATE**: k6 터미널 출력으로 부하 테스트 결과 확인
5. MVP 완료 — 부하 테스트 실행 자체만으로 가치 제공

### Incremental Delivery

1. Setup + Foundational → 인프라 준비
2. +US1 (k6) → 터미널 부하 테스트 가능 (MVP!)
3. +US2 (Prometheus) → 시계열 메트릭 수집/쿼리
4. +US3 (Grafana) → 실시간 시각화 대시보드
5. +US4 (통합) → 원클릭 실행 완성

---

## Notes

- 총 19개 태스크 (Setup 3 + Foundational 5 + US1 3 + US2 2 + US3 2 + US4 2 + Polish 2)
- 프로덕션 코드 변경: build.gradle.kts, application.yml 2개 파일만
- 신규 파일 약 10개: prometheus.yml, Grafana provisioning 3개, 대시보드 JSON 2개, k6 스크립트 3개
- docker-compose.yml 수정: prometheus, grafana, k6 서비스 추가
- US1~US4는 순차 의존 (k6 → Prometheus → Grafana → 통합)
