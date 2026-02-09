# Tasks: k6 부하 테스트 + Prometheus/Grafana 시각화

**Input**: Design documents from `/specs/005-k6-load-test-visualization/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: 이번 Phase의 테스트는 k6 스크립트 자체가 부하 테스트이므로, US1 구현 태스크가 곧 테스트 코드 작성이다.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup (프로덕션 코드 변경)

**Purpose**: Spring Boot Actuator + Micrometer로 메트릭 엔드포인트 노출

- [ ] T001 `build.gradle.kts` 수정 — `implementation("org.springframework.boot:spring-boot-starter-actuator")` + `runtimeOnly("io.micrometer:micrometer-registry-prometheus")` 의존성 추가
- [ ] T002 `src/main/resources/application.yml` 수정 — `management.endpoints.web.exposure.include: health,info,metrics,prometheus`, `management.endpoint.prometheus.enabled: true`, `management.metrics.tags.application: proximity-service` 추가

**Checkpoint**: `GET /actuator/prometheus` 접속 시 Prometheus 텍스트 형식 메트릭 반환 확인

---

## Phase 2: Foundational (Docker Compose 모니터링 스택)

**Purpose**: Prometheus + Grafana + k6 서비스를 Docker Compose에 추가

**⚠️ CRITICAL**: 모든 User Story의 기반 인프라. 이 Phase 완료 후 k6 스크립트 작성 가능.

- [ ] T003 `monitoring/prometheus/prometheus.yml` 생성 — `global.scrape_interval: 15s`, `scrape_configs: [{job_name: spring-boot-app, metrics_path: /actuator/prometheus, scrape_interval: 5s, static_configs: [{targets: [app:8080]}]}, {job_name: prometheus, static_configs: [{targets: [localhost:9090]}]}]`
- [ ] T004 [P] `monitoring/grafana/provisioning/datasources/datasource.yml` 생성 — Prometheus 데이터소스 (`url: http://prometheus:9090`, `isDefault: true`)
- [ ] T005 [P] `monitoring/grafana/provisioning/dashboards/dashboard-provider.yml` 생성 — 파일 기반 대시보드 프로바이더 (`path: /etc/grafana/provisioning/dashboards/json`, `folder: Load Tests`)
- [ ] T006 `docker-compose.yml` 수정 — prometheus 서비스 (prom/prometheus:v2.54.1, port 9090, `--web.enable-remote-write-receiver`), grafana 서비스 (grafana/grafana:11.4.0, port 3000, 프로비저닝 볼륨 마운트, `GF_AUTH_ANONYMOUS_ENABLED=true`), k6 서비스 (grafana/k6:0.55.0, `profiles: [test]`, `K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write`), 공통 네트워크 설정

**Checkpoint**: `docker compose up -d` 실행 후 http://localhost:9090 (Prometheus) + http://localhost:3000 (Grafana) 접속 확인, Prometheus targets에서 spring-boot-app이 UP 상태

---

## Phase 3: User Story 1 - k6 부하 테스트 실행 (Priority: P1) 🎯 MVP

**Goal**: k6 스크립트로 주변 검색 + CRUD 혼합 부하를 발생시키고 터미널에서 p50/p95/p99, RPS, 에러율 확인

**Independent Test**: `docker compose --profile test run --rm k6 run /scripts/nearby-search.js` 실행 후 터미널에 결과 출력 확인

- [ ] T007 [US1] `k6/scripts/helpers/seed-data.js` 생성 — `seedBusinesses(baseUrl, count)` 함수: POST /v1/businesses/seed 호출하여 서울 중심 반경 15km에 지정 건수 시딩, 서울 5대 랜드마크 좌표 상수
- [ ] T008 [US1] `k6/scripts/nearby-search.js` 생성 — setup()에서 1,000건 시딩, stages: [{duration:10s, target:10}, {duration:10s, target:50}, {duration:30s, target:50}, {duration:5s, target:0}], default()에서 GET /v1/search/nearby (랜드마크 좌표 랜덤 선택, radius=5000), checks(status===200, 결과배열 길이>0), thresholds: {http_req_duration: [p(95)<500], http_req_failed: [rate<0.01]}
- [ ] T009 [P] [US1] `k6/scripts/crud-mixed.js` 생성 — setup()에서 500건 시딩, stages: [{duration:10s, target:5}, {duration:10s, target:20}, {duration:30s, target:20}, {duration:5s, target:0}], default()에서 가중치 기반 API 호출 (검색60%/조회20%/생성10%/수정5%/삭제5%), 각 엔드포인트별 custom metrics(Counter), thresholds: {http_req_duration: [p(95)<1000], http_req_failed: [rate<0.01]}

**Checkpoint**: 두 k6 스크립트 모두 에러 없이 실행되고 터미널에 p95, RPS, 에러율 출력. nearby-search p95 < 500ms, crud-mixed 에러율 < 1%

---

## Phase 4: User Story 2 - Prometheus 메트릭 수집 (Priority: P2)

**Goal**: k6 메트릭이 Prometheus Remote Write로 수집되고, 앱 메트릭이 scrape로 수집되어 Prometheus UI에서 조회 가능

**Independent Test**: k6 실행 후 http://localhost:9090 에서 `k6_http_req_duration_p95`, `http_server_requests_seconds_count` 쿼리 성공

- [ ] T010 [US2] `k6/scripts/nearby-search.js` 수정 — `--out experimental-prometheus-rw` 출력 추가: `export const options` 내 `ext.loadimpact.projectID` 또는 환경변수 확인, `K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),min,max,avg` 설정이 docker-compose.yml에 반영되어 있는지 확인
- [ ] T011 [P] [US2] `k6/scripts/crud-mixed.js` 수정 — nearby-search.js와 동일하게 Prometheus Remote Write 출력 호환 확인, k6 custom metrics 이름에 `k6_` prefix 적용 (`k6_crud_create_total` 등)

**Checkpoint**: k6 실행 후 Prometheus UI (`http://localhost:9090/graph`)에서 `k6_http_req_duration_p95`, `k6_http_reqs_total`, `http_server_requests_seconds_count` 쿼리 시 데이터 확인

---

## Phase 5: User Story 3 - Grafana 대시보드 시각화 (Priority: P3)

**Goal**: 사전 구성된 대시보드에서 앱 메트릭 + k6 메트릭을 실시간 그래프로 확인

**Independent Test**: http://localhost:3000 접속 → "Load Tests" 폴더 → 대시보드 선택 → 패널에 데이터 표시

- [ ] T012 [US3] `monitoring/grafana/provisioning/dashboards/json/proximity-service-dashboard.json` 생성 — 5개 패널: (1) HTTP 요청 레이턴시 p50/p95/p99 (`histogram_quantile`), (2) 초당 요청 수 RPS (`rate(http_server_requests_seconds_count[1m])`), (3) HTTP 에러율 (`rate(http_server_requests_seconds_count{status=~"5.."}[1m])`), (4) JVM 힙 메모리 (`jvm_memory_used_bytes{area="heap"}`), (5) HikariCP 커넥션 풀 (`hikaricp_connections_active`)
- [ ] T013 [P] [US3] `monitoring/grafana/provisioning/dashboards/json/k6-load-test-dashboard.json` 생성 — 4개 패널: (1) k6 가상 사용자 수 (`k6_vus`), (2) k6 HTTP 요청 레이턴시 p95/p99 (`k6_http_req_duration_p95`), (3) k6 초당 요청 수 (`rate(k6_http_reqs_total[1m])`), (4) k6 에러율 (`k6_http_req_failed`)

**Checkpoint**: `docker compose up -d` → k6 부하 테스트 실행 → Grafana 대시보드에서 실시간 그래프 업데이트 확인, 테스트 완료 후 시간 범위 조절하여 전체 추이 확인

---

## Phase 6: User Story 4 - 원클릭 통합 실행 (Priority: P4)

**Goal**: 단일 명령어로 전체 스택 시작/테스트/정리가 가능한 개발자 경험

**Independent Test**: README 또는 quickstart.md의 명령어를 순서대로 실행하여 전체 흐름 확인

- [ ] T014 [US4] `docker-compose.yml` 최종 검증 — 모든 서비스 헬스체크 설정 확인 (app: `/actuator/health`, prometheus: `/-/healthy`, grafana: `/api/health`), depends_on 순서 확인 (mysql → redis → app → prometheus → grafana), k6 서비스의 depends_on에 app, prometheus 포함
- [ ] T015 [US4] 통합 실행 흐름 검증 — (1) `docker compose up -d` → 전체 서비스 healthy, (2) `docker compose --profile test run --rm k6 run /scripts/nearby-search.js` → 터미널 결과 + Grafana 그래프 확인, (3) `docker compose --profile test run --rm k6 run /scripts/crud-mixed.js` → 터미널 결과 + Grafana 그래프 확인, (4) `docker compose down -v` → 모든 리소스 정리

**Checkpoint**: quickstart.md의 전체 흐름이 문제없이 동작

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 최종 정리 및 검증

- [ ] T016 [P] `.gitignore` 수정 — 필요시 `monitoring/grafana/data/`, `monitoring/prometheus/data/` 등 볼륨 데이터 디렉토리 제외
- [ ] T017 전체 검증 — 기존 `./gradlew test` 통과 확인 (87개 테스트, Actuator 추가가 기존 테스트에 영향 없는지), `./gradlew performanceTest` 통과 확인 (11개 테스트)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 의존 없음 — 즉시 시작
- **Phase 2 (Foundational)**: Phase 1 완료 후 — 모든 User Story 차단
- **Phase 3 (US1: k6 스크립트)**: Phase 2 완료 후
- **Phase 4 (US2: Prometheus 수집)**: Phase 3 완료 후 (k6 스크립트가 있어야 메트릭 수집 테스트 가능)
- **Phase 5 (US3: Grafana 대시보드)**: Phase 4 완료 후 (수집된 메트릭이 있어야 대시보드 검증 가능)
- **Phase 6 (US4: 통합 실행)**: Phase 3/4/5 모두 완료 후
- **Phase 7 (Polish)**: Phase 6 완료 후

### User Story Dependencies

- **US1 (P1)**: Phase 2 완료 후 시작 가능 — 다른 US에 의존 없음
- **US2 (P2)**: US1 완료 후 — k6 스크립트 존재 필요
- **US3 (P3)**: US2 완료 후 — Prometheus에 수집된 메트릭 필요
- **US4 (P4)**: US1~US3 모두 완료 후 — 전체 스택 통합 검증

### Within Each Phase

- T004, T005는 병렬 가능 (서로 다른 파일)
- T008, T009는 병렬 가능 (서로 다른 k6 스크립트)
- T012, T013은 병렬 가능 (서로 다른 대시보드 JSON)
- T010, T011은 병렬 가능 (서로 다른 k6 스크립트 수정)

### Parallel Opportunities

```text
Phase 2:
  T004 (datasource.yml) || T005 (dashboard-provider.yml)

Phase 3:
  T008 (nearby-search.js) → T009 (crud-mixed.js) 순차 권장 (헬퍼 공유)
  또는 T007 완료 후 T008 || T009 병렬 가능

Phase 4:
  T010 (nearby-search 수정) || T011 (crud-mixed 수정)

Phase 5:
  T012 (앱 대시보드) || T013 (k6 대시보드)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Actuator 의존성 + 설정 (T001~T002)
2. Phase 2: Docker Compose 모니터링 스택 (T003~T006)
3. Phase 3: k6 스크립트 작성 (T007~T009)
4. **STOP and VALIDATE**: k6 터미널 출력으로 p95, RPS, 에러율 확인
5. 이것만으로 "부하 테스트 실행" 가치 전달 완료

### Incremental Delivery

1. Setup + Foundational → 인프라 준비 완료
2. +US1 (k6 스크립트) → 터미널에서 부하 테스트 결과 확인 가능 (MVP!)
3. +US2 (Prometheus) → 메트릭이 시계열로 저장, Prometheus UI에서 쿼리 가능
4. +US3 (Grafana) → 대시보드에서 시각적으로 성능 확인 가능
5. +US4 (통합) → 원클릭 실행 경험 완성

---

## Notes

- 총 17개 태스크 (Setup 2 + Foundational 4 + US1 3 + US2 2 + US3 2 + US4 2 + Polish 2)
- 프로덕션 코드 변경: build.gradle.kts, application.yml 2개 파일만
- 신규 파일 약 10개: prometheus.yml, datasource.yml, dashboard-provider.yml, 대시보드 JSON 2개, k6 스크립트 3개, docker-compose.yml 수정
- US1~US4는 순차 의존 (k6 → Prometheus → Grafana → 통합)
