# Data Model: k6 부하 테스트 + Prometheus/Grafana 시각화

## 기존 엔티티 (수정 없음)

이번 Phase에서 비즈니스 도메인 데이터 모델은 변경하지 않는다.
Business 엔티티와 Redis GEO 인덱스는 Phase 1~3에서 정의한 그대로 사용한다.

## 프로덕션 코드 변경 (최소)

### Spring Boot Actuator 메트릭 엔드포인트

| 항목 | 내용 |
|------|------|
| 추가 의존성 | `spring-boot-starter-actuator`, `micrometer-registry-prometheus` |
| 노출 엔드포인트 | `GET /actuator/prometheus` |
| 자동 수집 메트릭 | HTTP 요청, JVM 메모리/GC/스레드, HikariCP 커넥션 풀 |

비즈니스 로직 변경 없음. 의존성 추가와 application.yml 설정만으로 동작.

## 인프라 설정 파일

### Docker Compose 서비스 (추가)

| 서비스 | 이미지 | 포트 | 역할 |
|--------|--------|------|------|
| prometheus | prom/prometheus:v2.54.1 | 9090 | 메트릭 수집 + 시계열 저장 |
| grafana | grafana/grafana:11.4.0 | 3000 | 메트릭 시각화 대시보드 |
| k6 | grafana/k6:0.55.0 | - | 부하 테스트 실행 (profiles: test) |

### Prometheus 수집 대상

| Job | Target | 메트릭 경로 | 수집 간격 |
|-----|--------|------------|----------|
| spring-boot-app | app:8080 | /actuator/prometheus | 5s |
| prometheus | localhost:9090 | /metrics | 15s |

k6 메트릭은 Remote Write(push)로 수집되므로 별도 scrape job 불필요.

### Grafana 대시보드 패널

| # | 패널 | 데이터 소스 | 시각화 유형 |
|---|------|-----------|-----------|
| 1 | HTTP 요청 레이턴시 (p50/p95/p99) | Prometheus | Time Series |
| 2 | 초당 요청 수 (RPS) | Prometheus | Time Series |
| 3 | HTTP 에러율 | Prometheus | Gauge + Time Series |
| 4 | JVM 힙 메모리 사용량 | Prometheus | Time Series |
| 5 | HikariCP 커넥션 풀 | Prometheus | Time Series |
| 6 | k6 가상 사용자 수 (VUs) | Prometheus | Time Series |
| 7 | k6 요청 레이턴시 | Prometheus | Time Series |

## k6 부하 테스트 시나리오

### 시나리오 1: 주변 검색 (nearby-search)

| 항목 | 값 |
|------|-----|
| 대상 API | GET /v1/search/nearby |
| 시딩 | 테스트 시작 시 1,000건 사업장 등록 |
| 가상 사용자 | 10 → 50 → 10 (ramp-up/steady/ramp-down) |
| 실행 시간 | 10s ramp-up + 30s steady + 5s ramp-down |
| 성공 기준 | p(95) < 500ms, 에러율 < 1% |

### 시나리오 2: CRUD 혼합 (crud-mixed)

| 항목 | 값 |
|------|-----|
| 대상 API | POST/GET/PUT/DELETE /v1/businesses |
| 비율 | 검색 60% + 조회 20% + 생성 10% + 수정 5% + 삭제 5% |
| 가상 사용자 | 5 → 20 → 5 |
| 실행 시간 | 10s ramp-up + 30s steady + 5s ramp-down |
| 성공 기준 | p(95) < 1000ms, 에러율 < 1% |

## 테스트 데이터

k6 시딩에 사용하는 좌표는 Phase 4에서 정의한 서울 5대 랜드마크를 재사용:

| 랜드마크 | 위도 | 경도 |
|---------|------|------|
| 강남 | 37.4979 | 127.0276 |
| 홍대 | 37.5563 | 126.9236 |
| 잠실 | 37.5133 | 127.1001 |
| 서울역 | 37.5547 | 126.9707 |
| 명동 | 37.5636 | 126.9869 |
