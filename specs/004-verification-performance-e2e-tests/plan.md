# Implementation Plan: 책 조건 검증 + 성능 + E2E 테스트

**Branch**: `004-verification-performance-e2e-tests` | **Date**: 2026-02-09 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-verification-performance-e2e-tests/spec.md`

## Summary

Phase 1~3에서 구현한 Proximity Service MVP가 『대규모 시스템 설계 기초 2』 1장의 핵심 설계 원칙을 만족하는지 체계적으로 검증하는 테스트 스위트를 추가한다.
1) 책의 설계 원칙 검증 (Geohash 정확도, Read/Write 분리, 이중 저장소 정합성, 캐싱 전략, 동기화 복구)
2) 성능 측정 (검색 레이턴시 p50/p95/p99, 배치 처리 속도, 동시 검색, 대량 데이터)
3) 실제 사용 E2E 시나리오 (사업장 생애주기, Redis 장애 복구, 불일치 수정, 다중 위치 검색)

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: Spring Boot 3.4.1, JUnit 5, AssertJ, Testcontainers (MySQL 8.0 + Redis 7), MockMvc
**Testing Approach**: 싱글톤 Testcontainers + `@DynamicPropertySource` 패턴으로 컨테이너 재사용
**Performance Goals**: p95 ≤ 100ms (1,000건), fullSync 1,000건 ≤ 10초
**Constraints**: 프로덕션 코드 변경 없음, 기존 43개 테스트 무영향

## Project Structure

### Test Files (this feature)

```text
src/test/java/com/proximityservice/
  support/                                    # 공통 인프라
    BaseIntegrationTest.java                  # 싱글톤 Testcontainers 베이스 클래스
    TestDataFactory.java                      # 서울 좌표 + Haversine + 데이터 생성
  verification/                               # 책 조건 검증 (30개 테스트)
    GeohashAccuracyTest.java                  # Geohash 정확도 & 경계 (8)
    ReadWriteSeparationTest.java              # Read/Write 분리 경로 (6)
    DualStorageConsistencyTest.java           # MySQL↔Redis 정합성 (6)
    CachingStrategyTest.java                  # 캐싱 전략 (4)
    SyncRecoveryVerificationTest.java         # 동기화 & 복구 (6)
  performance/                                # 성능 테스트 (11개, @Tag)
    SearchLatencyTest.java                    # p50/p95/p99 레이턴시 (3)
    BatchProcessingPerformanceTest.java       # 배치 처리 속도 (3)
    ConcurrentSearchTest.java                 # 동시 검색 정확성 (2)
    LargeDatasetTest.java                     # 만건+ 대량 데이터 (3)
  scenario/                                   # E2E 시나리오 (4개 테스트)
    BusinessLifecycleScenarioTest.java        # 등록→검색→수정→삭제 전체 흐름
    SyncRecoveryScenarioTest.java             # Redis 장애→복구
    ConsistencyCheckScenarioTest.java         # 불일치 감지→수정
    MultiLocationSearchScenarioTest.java      # 서울 다중 위치 검색
```

### Modified Files

- `build.gradle.kts`: 성능 테스트 태그 분리 (`excludeTags("performance")` / `performanceTest` task)

## Implementation Strategy

### Step 1: 공통 인프라 (support/)
- `TestDataFactory`: 서울 5대 랜드마크 좌표 상수, Haversine 거리 계산, 대량 데이터 생성기(seed=42)
- `BaseIntegrationTest`: 싱글톤 Testcontainers, `@DynamicPropertySource`, 공통 빈 주입, `@BeforeEach` 클린업

### Step 2: 책 조건 검증 (verification/, 30개 테스트)
- 각 설계 원칙별 독립 테스트 클래스 → BaseIntegrationTest 상속

### Step 3: 성능 테스트 (performance/, 11개 테스트)
- `@Tag("performance")`로 일반 테스트와 분리
- 워밍업 10회 후 100회 측정, p50/p95/p99/min/max 출력

### Step 4: E2E 시나리오 (scenario/, 4개 테스트)
- MockMvc 기반 HTTP 전체 흐름 검증

### Step 5: 빌드 설정
- `tasks.named<Test>("test")` → excludeTags("performance")
- `tasks.register<Test>("performanceTest")` → includeTags("performance")

## Key Decisions

### D-001: Testcontainers 싱글톤 패턴

**Decision**: static initializer + `@DynamicPropertySource` 사용 (`@Container`/`@Testcontainers` 미사용)

**Rationale**: `@Container` + `@Testcontainers`를 abstract 부모 클래스에 사용하면, JUnit이 첫 번째 자식 클래스 완료 시 컨테이너를 중지하여 후속 클래스에서 연결 실패 발생. 싱글톤 패턴으로 JVM 전체에서 컨테이너 1세트만 유지.

### D-002: Geohash 동일 좌표 거리 허용 범위

**Decision**: 동일 좌표 거리 검증에 `distance < 1.0m` 허용

**Rationale**: Redis는 좌표를 52비트 geohash 정수로 양자화하므로, 동일 좌표도 ~0.x 미터 오차 발생. `distance == 0.0` 검증은 거짓 실패를 유발.

### D-003: 성능 테스트 태그 분리

**Decision**: `tasks.named<Test>("test")`로 범위 한정 (NOT `tasks.withType<Test>`)

**Rationale**: `tasks.withType<Test>`는 custom `performanceTest` task에도 적용되어 태그 충돌(동시 include+exclude) 발생.
