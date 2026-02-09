# Feature Specification: 책 조건 검증 + 성능 + E2E 테스트

**Feature Branch**: `004-verification-performance-e2e-tests`
**Created**: 2026-02-09
**Status**: Completed
**Input**: 『가상 면접 사례로 배우는 대규모 시스템 설계 기초 2』 1장 Proximity Service 설계 원칙 검증

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 책의 핵심 설계 원칙 체계적 검증 (Priority: P1)

Phase 1~3에서 구현한 Proximity Service MVP가 책에서 제시한 핵심 설계 원칙(Geohash 정확도, Read/Write 분리, 이중 저장소 정합성, 캐싱 전략, 동기화 복구)을 실제로 만족하는지 증명한다.

**Why this priority**: 기존 43개 테스트는 기능 동작을 검증하지만, 책의 설계 원칙 준수 여부를 체계적으로 검증하지 않는다. 학습 목적의 프로젝트에서 설계 원칙 검증은 핵심 가치다.

**Acceptance Scenarios**:

1. **Given** 강남 기준 30m/70m 거리의 두 업체가 등록되어 있을 때, **When** 50m 반경으로 검색하면, **Then** 30m 업체만 포함되고 70m 업체는 제외된다.
2. **Given** MySQL에만 업체가 저장되어 있을 때(Redis 동기화 없이), **When** 주변 검색을 실행하면, **Then** 결과가 0건이다 (Read 경로가 Redis임을 증명).
3. **Given** 업체 생성 후 MySQL과 Redis 모두에 저장되어 있을 때, **When** 메타데이터만 변경하면, **Then** Redis에는 변동이 없다.
4. **Given** Redis GEO 인덱스가 삭제된 상태에서, **When** fullSync를 실행하면, **Then** MySQL의 모든 업체가 Redis에 복구된다.

---

### User Story 2 - 성능 기준 충족 검증 (Priority: P2)

검색 레이턴시, 배치 처리 속도, 동시 검색 정확성, 대량 데이터 처리 성능이 책에서 제시한 기준을 충족하는지 측정한다.

**Why this priority**: 성능 기준을 수치로 증명해야 시스템 설계가 적절한지 판단할 수 있다.

**Acceptance Scenarios**:

1. **Given** 10,000건 데이터가 존재할 때, **When** 검색을 100회 반복 측정하면, **Then** p95 레이턴시가 200ms 이내이다.
2. **Given** MySQL에 1,000건이 존재할 때, **When** fullSync를 실행하면, **Then** 10초 이내에 완료된다.
3. **Given** 100건 데이터가 존재할 때, **When** 10 스레드가 동시에 50회 검색하면, **Then** 예외 없이 모든 결과가 정확하다.

---

### User Story 3 - 실제 사용 시나리오 E2E 검증 (Priority: P3)

실제 운영 환경에서 발생할 수 있는 전체 흐름(사업장 생애주기, Redis 장애 복구, 불일치 수정, 다중 위치 검색)을 엔드투엔드로 검증한다.

**Why this priority**: 개별 기능 테스트로는 확인하기 어려운 전체 흐름의 정합성을 보장한다.

**Acceptance Scenarios**:

1. **Given** 서울 5개 랜드마크에 업체가 등록되어 있을 때, **When** 등록→검색→수정(좌표이동)→삭제의 전체 생애주기를 실행하면, **Then** 각 단계에서 예상대로 동작한다.
2. **Given** 10개 업체가 정상 등록된 후 Redis 인덱스가 삭제되면, **When** fullSync를 실행하면, **Then** 10개 모두 복구되어 검색이 다시 가능하다.
3. **Given** MySQL과 Redis 간 불일치가 발생했을 때(누락 1건, 고아 2건), **When** consistencyCheck를 실행하면, **Then** 정합 상태(5:5, ID 일치)로 복원된다.

## Success Criteria

| # | 기준 | 측정 방법 |
|---|------|----------|
| SC-001 | Geohash 검색 정확도: 반경 내 포함, 반경 외 제외 | GeohashAccuracyTest 8개 테스트 통과 |
| SC-002 | Read/Write 분리: Read=Redis, Write=MySQL+Redis | ReadWriteSeparationTest 6개 테스트 통과 |
| SC-003 | 이중 저장소 정합성: CRUD 후 양쪽 일치 | DualStorageConsistencyTest 6개 테스트 통과 |
| SC-004 | 검색 p95 ≤ 100ms (1,000건), ≤ 200ms (10,000건) | SearchLatencyTest 측정 |
| SC-005 | fullSync 1,000건 ≤ 10초 | BatchProcessingPerformanceTest 측정 |
| SC-006 | 동시 검색 무에러 | ConcurrentSearchTest 통과 |
| SC-007 | E2E 시나리오 전체 통과 | 4개 시나리오 테스트 통과 |

## Assumptions & Constraints

- 프로덕션 코드 변경 없음 (테스트 코드만 추가)
- 기존 43개 테스트에 영향 없음
- 성능 테스트는 `@Tag("performance")`로 분리하여 일반 테스트와 독립 실행
- Testcontainers 기반으로 실제 MySQL + Redis에서 테스트 (Mock 아님)
