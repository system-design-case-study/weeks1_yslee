# Proximity Service — 대규모 시스템 설계 케이스 스터디

『가상 면접 사례로 배우는 대규모 시스템 설계 기초 2』 1장 **Proximity Service**를 직접 구현하고, 부하 테스트를 통해 설계 원칙의 필요성을 검증한 프로젝트.

## 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **책 참고** | 가상 면접 사례로 배우는 대규모 시스템 설계 기초 2, 1장 |
| **핵심 기능** | 위치 기반 주변 사업장 검색 (Proximity Search) |
| **기술 스택** | Java 21, Spring Boot 3.4.1, MySQL 8.0, Redis 7 |
| **모니터링** | Prometheus + Grafana + k6 |
| **인프라** | Docker Compose (로컬) |

## 아키텍처

```
Client → Spring Boot API → Redis GEO (검색 인덱스)
                         → MySQL (원본 데이터)

k6 (부하 테스트) → API → Prometheus ← Grafana (시각화)
```

### 책의 설계 원칙 적용

| 원칙 | 구현 |
|------|------|
| **읽기/쓰기 분리** | Redis GEO = 읽기 전용 인덱스, MySQL = 원본 데이터 |
| **데이터 지역성** | Redis에 ID+좌표만 저장, 상세 정보는 MySQL PK 조회 |
| **검색 결과 제한** | GEOSEARCH COUNT로 최대 20건 반환 (페이지네이션) |
| **앱 레벨 동기화** | CRUD 시 MySQL + Redis 동시 갱신 |
| **배치 복구** | 전체 동기화 + 정합성 검증 배치 |

## 구현 Phase

| Phase | 브랜치 | 설명 |
|-------|--------|------|
| 1 | `001-nearby-search-api` | Redis GEO 기반 주변 검색 API |
| 2 | `002-business-crud-api` | 사업장 CRUD API + 앱 레벨 Redis 동기화 |
| 3 | `003-sync-recovery-batch` | 전체 동기화 + 정합성 검증 배치 |
| 4 | `004-verification-performance-e2e-tests` | 설계 원칙 검증 + 성능 + E2E 테스트 |
| 5 | `005-k6-load-test-visualization` | k6 부하 테스트 + Prometheus/Grafana 모니터링 |
| 6 | `006-performance-optimization` | 검색 페이지네이션 + DB 커넥션 풀 튜닝 |

## 부하 테스트 결과 & 실패에서 배운 것

이 프로젝트의 핵심 학습은 **소규모에서 잘 동작하는 코드가 대규모에서 무너지는 과정**을 직접 경험한 것이다.

### 책의 요구 규모

| 항목 | 수치 |
|------|------|
| 사업장 수 | 2억 건 |
| DAU | 1억 명 |
| 평균 QPS | 5,800 |
| 응답 시간 | 100ms 이내 |

### 테스트 1: 소규모 (1,000건, 50 VU) — 모두 통과

| 테스트 | p95 | RPS | 에러율 |
|--------|-----|-----|--------|
| nearby-search | 11ms | 326 | 0% |
| crud-mixed | 13ms | 161 | 0.05% |

소규모에서는 아무 문제가 없었다. 모든 성능 기준을 여유 있게 통과했다.

### 테스트 2: 대규모 (100,000건, 500 VU) — 완전 실패

| 테스트 | p95 | RPS | 에러율 |
|--------|-----|-----|--------|
| nearby-search | **41초** | 10 | 0% |
| crud-mixed | **60초** (타임아웃) | 4 | **29%** |

시스템이 완전히 무너졌다. 원인 분석:

#### 실패 원인 1: 검색 결과 수 제한 없음 (가장 치명적)

```
1,000건 → 반경 5km 내 수십 건 → 응답 수 KB → 문제없음
100,000건 → 반경 5km 내 수만 건 → 응답 2MB → 서버 마비
```

Redis GEOSEARCH가 반경 내 모든 결과를 반환하고, 그 전부를 MySQL에서 조회하여 응답에 포함했다. 요청 하나당 2MB 응답은 서버가 감당할 수 없었다.

#### 실패 원인 2: DB 커넥션 풀 고갈

HikariCP 기본 풀(10개)로 500 VU를 감당할 수 없었다. 각 요청이 수만 건을 처리하느라 커넥션을 오래 점유하면서 대기 큐가 폭발했다.

#### 실패 원인 3: 단일 서버 스레드 포화

톰캣 기본 스레드(200개)로 500 VU의 동시 요청을 처리할 수 없었다. 각 요청의 처리 시간이 길어지면서 스레드 반환이 느려지고 전체가 밀렸다.

### 테스트 3: 개선 후 (100,000건, 500 VU) — 대폭 개선

**적용한 개선:**
1. GEOSEARCH COUNT 옵션으로 검색 결과 최대 20건 제한
2. HikariCP 커넥션 풀 10 → 50으로 확대

| 테스트 | p95 | RPS | 에러율 | 변화 |
|--------|-----|-----|--------|------|
| nearby-search | **2.1초** | 155 | 0% | p95 20배 개선 |
| crud-mixed | **2.08초** | 81 | **0.10%** | 에러율 29%→0.1% |

**검색 결과 제한 하나**로 응답 크기가 2MB → 수 KB로 줄었고, 대부분의 성능 문제가 해소되었다.

### 아직 남은 과제 (책에서 다루는 추가 확장)

p95 100ms 목표는 여전히 미달(2.1초). 이는 로컬 Docker 단일 인스턴스 한계이며, 책에서 제시하는 추가 확장 전략이 필요하다:

| 확장 전략 | 기대 효과 |
|-----------|----------|
| 수평 확장 (Nginx + 멀티 인스턴스) | 동시 처리 능력 N배 |
| MySQL 읽기 복제본 분리 | DB 병목 해소 |
| 데이터 샤딩 (지역별 분산) | 2억 건 분산 처리 |
| 캐싱 레이어 추가 | 동일 지역 반복 검색 최적화 |

## 빠른 시작

### 전체 스택 실행

```bash
docker compose up -d
```

### 부하 테스트 실행

```bash
# 주변 검색 부하 테스트
docker compose --profile test run --rm k6 run --out experimental-prometheus-rw /scripts/nearby-search.js

# CRUD 혼합 부하 테스트
docker compose --profile test run --rm k6 run --out experimental-prometheus-rw /scripts/crud-mixed.js
```

### 모니터링 확인

| 서비스 | URL |
|--------|-----|
| Grafana 대시보드 | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| API | http://localhost:8080 |

### 유닛 테스트

```bash
./gradlew test
```

### 종료

```bash
docker compose down -v
```

## API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET | `/v1/search/nearby?latitude=&longitude=&radius=&limit=` | 주변 검색 |
| POST | `/v1/businesses` | 사업장 등록 |
| GET | `/v1/businesses/{id}` | 사업장 조회 |
| PUT | `/v1/businesses/{id}` | 사업장 수정 |
| DELETE | `/v1/businesses/{id}` | 사업장 삭제 |
| POST | `/v1/businesses/seed` | 대량 시딩 |

## 프로젝트 구조

```
src/main/java/com/proximityservice/
├── controller/         # REST API 컨트롤러
├── service/            # 비즈니스 로직
├── repository/         # MySQL JPA + Redis GEO
├── domain/             # JPA 엔티티
├── dto/                # 요청/응답 DTO
├── config/             # Redis 설정
├── batch/              # 동기화/정합성 배치
└── exception/          # 예외 처리

k6/scripts/             # k6 부하 테스트 스크립트
monitoring/             # Prometheus + Grafana 설정
specs/                  # 설계 문서 (Phase별)
```
