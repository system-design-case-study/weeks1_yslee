# Tasks: 책 조건 검증 + 성능 + E2E 테스트

**Input**: Design documents from `/specs/004-verification-performance-e2e-tests/`
**Prerequisites**: Phase 1~3 완료, plan.md, spec.md, research.md

**Tests**: 이번 Phase 자체가 테스트 추가이므로, 각 태스크가 곧 테스트 파일 생성이다.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: 공통 인프라 (support/)

**Purpose**: 모든 테스트 클래스에서 재사용하는 베이스 클래스와 유틸리티

- [x] T001 [P] `TestDataFactory.java` 생성 — 서울 5대 랜드마크 좌표 상수, haversineMeters(), generateCluster(), generateBulk(), 랜드마크별 팩토리 메서드 `src/test/java/com/proximityservice/support/TestDataFactory.java`
- [x] T002 [P] `BaseIntegrationTest.java` 생성 — 싱글톤 Testcontainers (MySQL 8.0 + Redis 7), @DynamicPropertySource, 공통 @Autowired 빈 8개, @BeforeEach 클린업, 헬퍼 메서드 `src/test/java/com/proximityservice/support/BaseIntegrationTest.java`

**Checkpoint**: 공통 인프라 완료 — 테스트 클래스 작성 시작 가능

---

## Phase 2: 책 조건 검증 (verification/, 30개 테스트)

**Goal**: 『대규모 시스템 설계 기초 2』 1장의 5가지 핵심 설계 원칙 검증

- [x] T003 [P] [US1] `GeohashAccuracyTest.java` — 8개 테스트 (50m/500m/20km 반경, 경계, 동일좌표, Haversine 비교, 정렬, 기본반경) `src/test/java/com/proximityservice/verification/GeohashAccuracyTest.java`
- [x] T004 [P] [US1] `ReadWriteSeparationTest.java` — 6개 테스트 (Redis 검색 경로, 고아 필터, 양쪽 저장, 양쪽 삭제, 메타변경 무영향, 좌표변경 반영) `src/test/java/com/proximityservice/verification/ReadWriteSeparationTest.java`
- [x] T005 [P] [US1] `DualStorageConsistencyTest.java` — 6개 테스트 (생성/다건/좌표변경/메타변경/삭제/부분삭제 정합성) `src/test/java/com/proximityservice/verification/DualStorageConsistencyTest.java`
- [x] T006 [P] [US1] `CachingStrategyTest.java` — 4개 테스트 (UUID member, geohash score, MySQL 상세조회, 고아 필터) `src/test/java/com/proximityservice/verification/CachingStrategyTest.java`
- [x] T007 [P] [US1] `SyncRecoveryVerificationTest.java` — 6개 테스트 (fullSync 재구축/가짜제거/좌표정확, consistencyCheck 추가/제거/혼합) `src/test/java/com/proximityservice/verification/SyncRecoveryVerificationTest.java`

**Checkpoint**: 5가지 설계 원칙 모두 30개 테스트로 검증 완료

---

## Phase 3: 성능 테스트 (performance/, 11개 테스트)

**Goal**: 검색 레이턴시, 배치 처리 속도, 동시 검색, 대량 데이터 성능 측정

- [x] T008 [P] [US2] `SearchLatencyTest.java` — 3개 테스트 (100건/1K건/10K건, 워밍업 10회 + 100회 측정, p50/p95/p99) `src/test/java/com/proximityservice/performance/SearchLatencyTest.java`
- [x] T009 [P] [US2] `BatchProcessingPerformanceTest.java` — 3개 테스트 (1K fullSync ≤10초, 5K fullSync ≤30초, 1K consistencyCheck) `src/test/java/com/proximityservice/performance/BatchProcessingPerformanceTest.java`
- [x] T010 [P] [US2] `ConcurrentSearchTest.java` — 2개 테스트 (10스레드×50검색, 5R+2W 동시) `src/test/java/com/proximityservice/performance/ConcurrentSearchTest.java`
- [x] T011 [P] [US2] `LargeDatasetTest.java` — 3개 테스트 (10K 정합성, 반경별 단조증가, 대량 정렬) `src/test/java/com/proximityservice/performance/LargeDatasetTest.java`

**Checkpoint**: 성능 기준 충족 여부 수치로 확인 가능

---

## Phase 4: E2E 시나리오 (scenario/, 4개 테스트)

**Goal**: 실제 운영 시나리오의 엔드투엔드 검증

- [x] T012 [US3] `BusinessLifecycleScenarioTest.java` — MockMvc HTTP 전체 흐름 (시딩→검색→상세→수정→이동→삭제→404→나머지건재) `src/test/java/com/proximityservice/scenario/BusinessLifecycleScenarioTest.java`
- [x] T013 [US3] `SyncRecoveryScenarioTest.java` — Redis 장애→fullSync 복구 (10개 생성→DEL→검색0건→fullSync→10개복구) `src/test/java/com/proximityservice/scenario/SyncRecoveryScenarioTest.java`
- [x] T014 [US3] `ConsistencyCheckScenarioTest.java` — 불일치 감지→수정 (5개→수동변조→consistencyCheck→정합복원) `src/test/java/com/proximityservice/scenario/ConsistencyCheckScenarioTest.java`
- [x] T015 [US3] `MultiLocationSearchScenarioTest.java` — 서울 다중 위치 검색 (5랜드마크+10클러스터→4위치검색→거리매트릭스) `src/test/java/com/proximityservice/scenario/MultiLocationSearchScenarioTest.java`

**Checkpoint**: E2E 시나리오 전체 통과

---

## Phase 5: 빌드 설정 & 검증

**Purpose**: 테스트 태그 분리 및 전체 검증

- [x] T016 `build.gradle.kts` 수정 — `tasks.named<Test>("test")` excludeTags("performance"), `tasks.register<Test>("performanceTest")` includeTags("performance")
- [x] T017 전체 테스트 실행 확인 (`./gradlew test` → 87 passed, `./gradlew performanceTest` → 11 passed)

---

## Dependencies & Execution Order

- **Phase 1 (인프라)**: 의존 없음 — 즉시 시작
- **Phase 2 (검증)**: Phase 1 완료 후. T003~T007 모두 병렬 가능 (서로 다른 파일)
- **Phase 3 (성능)**: Phase 1 완료 후. Phase 2와 병렬 가능. T008~T011 모두 병렬 가능
- **Phase 4 (시나리오)**: Phase 1 완료 후. Phase 2/3과 병렬 가능
- **Phase 5 (빌드)**: Phase 2/3/4 모두 완료 후

## Notes

- 총 17개 태스크 (인프라 2 + 검증 5 + 성능 4 + 시나리오 4 + 빌드 2)
- 프로덕션 코드 변경 없음
- Phase 2/3/4의 태스크들은 모두 서로 다른 파일이므로 대부분 병렬 가능
