# Tasks: 앱 레벨 동기화 + 복구용 배치

**Input**: Design documents from `/specs/003-sync-recovery-batch/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/batch-api.yaml

**Tests**: Phase 2에서 단위+통합 테스트를 함께 작성했으므로, 이번에도 각 User Story에 테스트를 포함한다.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: 의존성 추가 및 공통 인프라 설정

- [x] T001 `build.gradle`에 Spring Retry + AOP 의존성 추가
- [x] T002 Spring Retry 활성화 설정 `src/main/java/com/proximityservice/config/RetryConfig.java` 생성

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 User Story에서 사용하는 공통 컴포넌트

- [x] T003 `SyncBatchResult` DTO 생성 `src/main/java/com/proximityservice/dto/SyncBatchResult.java`
- [x] T004 `BusinessGeoRepository`에 전체 멤버 조회 메서드 추가 `src/main/java/com/proximityservice/repository/BusinessGeoRepository.java`
- [x] T005 `GlobalExceptionHandler`에 배치 중복 실행 에러(409) 핸들러 추가 `src/main/java/com/proximityservice/exception/GlobalExceptionHandler.java`

**Checkpoint**: 공통 DTO, Repository 확장, 예외 처리 완료 — User Story 구현 시작 가능

---

## Phase 3: User Story 1 - Redis 장애 후 검색 인덱스 복구 (Priority: P1)

**Goal**: MySQL 전체 데이터를 기반으로 Redis 검색 인덱스를 재구축하는 전체 동기화 배치 제공

**Independent Test**: Redis를 초기화한 뒤 배치를 실행하면, MySQL의 모든 사업장이 검색 가능해진다

### Implementation for User Story 1

- [x] T006 [US1] `SyncBatchService` 생성 — `fullSync()` 메서드 구현 (DEL → 페이지 단위 MySQL 조회 → GEOADD 파이프라인, AtomicBoolean 중복 실행 방지) `src/main/java/com/proximityservice/batch/SyncBatchService.java`
- [x] T007 [US1] `SyncBatchController` 생성 — `POST /v1/admin/sync/full` 엔드포인트 `src/main/java/com/proximityservice/controller/SyncBatchController.java`
- [x] T008 [US1] `SyncBatchServiceTest` 단위 테스트 작성 — fullSync 정상 동작, 빈 DB, 중복 실행 방지 `src/test/java/com/proximityservice/batch/SyncBatchServiceTest.java`
- [x] T009 [US1] `SyncBatchControllerTest` 통합 테스트 작성 — Redis 초기화 후 fullSync 실행 → 검색 확인 `src/test/java/com/proximityservice/controller/SyncBatchControllerTest.java`

**Checkpoint**: 전체 동기화 배치가 동작하며, Redis 장애 복구가 가능하다

---

## Phase 4: User Story 2 - 정합성 검증 및 보정 (Priority: P2)

**Goal**: MySQL-Redis 간 누락/고아 데이터를 찾아 보정하는 정합성 검증 배치 제공

**Independent Test**: MySQL에만 존재하는 사업장과 Redis에만 존재하는 고아 데이터가 정합성 검증 후 보정된다

### Implementation for User Story 2

- [x] T010 [US2] `SyncBatchService`에 `consistencyCheck()` 메서드 추가 — MySQL ID 집합 vs Redis 멤버 집합 비교, 누락 추가, 고아 제거 `src/main/java/com/proximityservice/batch/SyncBatchService.java`
- [x] T011 [US2] `SyncBatchController`에 `POST /v1/admin/sync/consistency-check` 엔드포인트 추가 `src/main/java/com/proximityservice/controller/SyncBatchController.java`
- [x] T012 [US2] `SyncBatchServiceTest`에 consistencyCheck 단위 테스트 추가 — 누락 보정, 고아 제거, 완전 일치 케이스 `src/test/java/com/proximityservice/batch/SyncBatchServiceTest.java`
- [x] T013 [US2] `SyncBatchControllerTest`에 정합성 검증 통합 테스트 추가 `src/test/java/com/proximityservice/controller/SyncBatchControllerTest.java`

**Checkpoint**: 정합성 검증 배치가 동작하며, 누락/고아 데이터가 자동 보정된다

---

## Phase 5: User Story 3 - 앱 레벨 동기화 실패 시 재시도 (Priority: P3)

**Goal**: 사업장 CRUD 시 Redis 동기화 실패 시 자동 재시도(최대 3회, 지수 백오프)

**Independent Test**: Redis 연결이 일시적으로 불안정할 때 사업장 등록 후 재시도를 통해 최종 Redis에 반영된다

### Implementation for User Story 3

- [x] T014 [US3] `BusinessService`의 Redis 동기화 로직을 별도 메서드로 추출하고 `@Retryable` 적용 `src/main/java/com/proximityservice/service/BusinessService.java`
- [x] T015 [US3] `@Recover` 메서드 구현 — 최종 실패 시 로그 기록 `src/main/java/com/proximityservice/service/BusinessService.java`
- [x] T016 [US3] `BusinessServiceRetryTest` 재시도 로직 테스트 작성 — 재시도 성공, 최종 실패 후 로그 기록 `src/test/java/com/proximityservice/service/BusinessServiceRetryTest.java`

**Checkpoint**: Redis 일시 장애 시에도 재시도를 통해 정합성이 유지된다

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 전체 배치 시스템의 완성도 확인

- [x] T017 quickstart.md 시나리오대로 전체 흐름 수동 검증
- [x] T018 전체 테스트 실행 확인 (Phase 1/2 기존 테스트 포함 전체 통과)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 의존 없음 — 즉시 시작
- **Phase 2 (Foundational)**: Phase 1 완료 후
- **Phase 3 (US1 — 전체 동기화)**: Phase 2 완료 후
- **Phase 4 (US2 — 정합성 검증)**: Phase 3 완료 후 (SyncBatchService에 메서드 추가)
- **Phase 5 (US3 — 재시도)**: Phase 2 완료 후 (Phase 3/4와 독립적으로 진행 가능)
- **Phase 6 (Polish)**: Phase 3, 4, 5 모두 완료 후

### User Story Dependencies

- **US1 (P1)**: Phase 2 완료 후 시작. 다른 US에 의존하지 않음
- **US2 (P2)**: US1 완료 후 시작 (SyncBatchService 파일 공유)
- **US3 (P3)**: Phase 2 완료 후 시작 가능. US1/US2와 독립적

### Parallel Opportunities

- T003, T004, T005 (Phase 2) — 서로 다른 파일이므로 병렬 가능
- US3 (Phase 5)는 US1/US2와 독립적이므로, Phase 2 완료 후 US1과 동시 진행 가능

---

## Parallel Example: User Story 1

```bash
# Phase 2 병렬 실행:
Task: T003 "SyncBatchResult DTO 생성"
Task: T004 "BusinessGeoRepository 멤버 조회 메서드 추가"
Task: T005 "GlobalExceptionHandler 409 핸들러 추가"

# US3는 US1과 병렬 가능:
Task: T006-T009 "US1: 전체 동기화 배치"  (동시에)
Task: T014-T016 "US3: 재시도 로직"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (의존성 추가)
2. Phase 2: Foundational (DTO, Repository 확장)
3. Phase 3: US1 — 전체 동기화 배치
4. **STOP and VALIDATE**: Redis 초기화 후 fullSync 실행 → 검색 확인

### Incremental Delivery

1. Setup + Foundational → 기반 완료
2. US1 (전체 동기화) → Redis 장애 복구 가능 (MVP)
3. US2 (정합성 검증) → 데이터 불일치 자동 보정
4. US3 (재시도) → 실시간 동기화 안정성 강화
5. Polish → 전체 검증

---

## Notes

- 총 18개 태스크 (Setup 2 + Foundational 3 + US1 4 + US2 4 + US3 3 + Polish 2)
- US1/US2는 SyncBatchService 파일을 공유하므로 순차 진행 권장
- US3는 BusinessService를 수정하므로 US1/US2와 독립적으로 병렬 가능
- Phase 2에서 작성한 기존 테스트(25개)가 깨지지 않도록 주의
