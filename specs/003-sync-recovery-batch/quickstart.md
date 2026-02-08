# Quickstart: 앱 레벨 동기화 + 복구용 배치

## 사전 조건

- Phase 1 (주변 검색 API) 완료
- Phase 2 (사업장 CRUD API) 완료
- Docker Compose 실행 (MySQL + Redis)

## 구현 순서

### 1. Spring Retry 의존성 추가

`build.gradle`에 `spring-retry` 및 `spring-boot-starter-aop` 의존성 추가.

### 2. BusinessGeoRepository 확장

Redis GEO의 전체 멤버 목록을 조회하는 메서드 추가 (ZRANGE로 전체 member 조회).

### 3. SyncBatchResult DTO 생성

배치 실행 결과를 담는 record 생성.

### 4. SyncBatchService 구현

- `fullSync()`: DEL → 페이지 단위 MySQL 조회 → GEOADD 파이프라인
- `consistencyCheck()`: MySQL ID 집합 vs Redis 멤버 집합 비교 → 누락 추가, 고아 제거
- AtomicBoolean으로 중복 실행 방지

### 5. BusinessService 재시도 로직 추가

기존 Redis 동기화 호출에 Spring Retry 적용 (`@Retryable`).

### 6. SyncBatchController 생성

- `POST /v1/admin/sync/full` → 전체 동기화 실행
- `POST /v1/admin/sync/consistency-check` → 정합성 검증 실행

### 7. RetryConfig 설정

`@EnableRetry` 활성화 및 재시도 설정.

### 8. 테스트 작성

- 단위 테스트: SyncBatchService (Mockito)
- 통합 테스트: SyncBatchController (Testcontainers)
- 재시도 테스트: BusinessService Redis 재시도 동작 검증

## 검증 방법

```bash
# 1. 사업장 몇 건 등록
curl -X POST http://localhost:8080/v1/businesses \
  -H "Content-Type: application/json" \
  -d '{"name":"테스트 식당","address":"서울시","latitude":37.5,"longitude":127.0,"category":"korean_food"}'

# 2. Redis 초기화 (장애 시뮬레이션)
docker exec redis redis-cli DEL geo:businesses

# 3. 전체 동기화 실행
curl -X POST http://localhost:8080/v1/admin/sync/full

# 4. 검색 확인
curl "http://localhost:8080/v1/search/nearby?latitude=37.5&longitude=127.0&radius=5000"

# 5. 정합성 검증
curl -X POST http://localhost:8080/v1/admin/sync/consistency-check
```
