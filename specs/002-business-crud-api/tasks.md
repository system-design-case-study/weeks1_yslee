# Tasks: 사업장 CRUD API

**Input**: Design documents from `/specs/002-business-crud-api/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Phase 1: Setup

**Purpose**: Phase 2에 필요한 공통 코드 준비 (기존 Phase 1 프로젝트 기반)

- [x] T001 [P] Create Category enum in `src/main/java/com/proximityservice/domain/Category.java` — 10개 카테고리 값 정의, `fromValue(String)` 정적 메서드로 소문자 문자열 ↔ enum 변환 지원
- [x] T002 [P] Create BusinessNotFoundException in `src/main/java/com/proximityservice/exception/BusinessNotFoundException.java` — RuntimeException 상속, businessId를 메시지에 포함
- [x] T003 Add BusinessNotFoundException handler to `src/main/java/com/proximityservice/exception/GlobalExceptionHandler.java` — 404 응답 + ErrorResponse 반환

**Checkpoint**: 공통 타입과 예외 처리 준비 완료

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 User Story에서 사용하는 엔티티 수정과 DTO/Service 기반 코드

**⚠️ CRITICAL**: User Story 구현 전 반드시 완료해야 함

- [x] T004 Add `update()` method to Business entity in `src/main/java/com/proximityservice/domain/Business.java` — 모든 필드를 갱신하고 좌표 변경 여부(boolean)를 반환. category 파라미터는 String으로 받되 Category enum 검증은 서비스 레이어에서 수행
- [x] T005 [P] Create BusinessCreateRequest DTO in `src/main/java/com/proximityservice/dto/BusinessCreateRequest.java` — record 타입, Jakarta Validation 적용 (name: @NotBlank, latitude: @NotNull @DecimalMin("-90") @DecimalMax("90"), longitude: @NotNull @DecimalMin("-180") @DecimalMax("180"), category: @NotBlank, address/phone/hours: optional)
- [x] T006 [P] Create BusinessUpdateRequest DTO in `src/main/java/com/proximityservice/dto/BusinessUpdateRequest.java` — BusinessCreateRequest와 동일한 필드/검증 규칙, record 타입
- [x] T007 [P] Create BusinessDetailResponse DTO in `src/main/java/com/proximityservice/dto/BusinessDetailResponse.java` — record 타입, Business 엔티티에서 변환하는 정적 팩토리 메서드 `from(Business)` 포함. JSON 필드명은 snake_case (created_at, updated_at)
- [x] T008 Create BusinessService in `src/main/java/com/proximityservice/service/BusinessService.java` — CRUD 메서드 4개 (create, getById, update, delete). Category enum 검증, BusinessNotFoundException 처리, ADR-4에 따른 Redis 동기화 로직 포함 (create: GEOADD, delete: ZREM, update: 좌표 변경 시에만 ZREM+GEOADD)

**Checkpoint**: Foundation ready — User Story 구현 시작 가능

---

## Phase 3: User Story 1 — 사업장 상세 조회 (Priority: P1) 🎯 MVP

**Goal**: 사업장 ID로 전체 상세 정보를 조회할 수 있다

**Independent Test**: `GET /v1/businesses/{id}` 호출 시 전체 필드 반환 확인, 없는 ID는 404

### Implementation for User Story 1

- [x] T009 [US1] Add `GET /v1/businesses/{id}` endpoint to `src/main/java/com/proximityservice/controller/BusinessController.java` — BusinessService.getById() 호출, 200 OK + BusinessDetailResponse 반환

**Checkpoint**: 시드 데이터로 등록된 사업장을 ID로 상세 조회 가능

---

## Phase 4: User Story 2 — 신규 사업장 등록 (Priority: P2)

**Goal**: 사업장 정보를 등록하고 즉시 주변 검색에 노출시킨다

**Independent Test**: `POST /v1/businesses`로 등록 후 `GET /v1/search/nearby`에서 검색 확인

### Implementation for User Story 2

- [x] T010 [US2] Add `POST /v1/businesses` endpoint to `src/main/java/com/proximityservice/controller/BusinessController.java` — @Valid BusinessCreateRequest 수신, BusinessService.create() 호출, 201 Created + BusinessDetailResponse 반환

**Checkpoint**: 등록 후 주변 검색 API에서 즉시 검색 가능

---

## Phase 5: User Story 3 — 사업장 정보 수정 (Priority: P3)

**Goal**: 사업장 정보를 수정하고, 좌표 변경 시에만 검색 인덱스를 갱신한다

**Independent Test**: `PUT /v1/businesses/{id}`로 수정 후 `GET`으로 변경 확인. 좌표 변경 시 새 위치에서 검색 확인

### Implementation for User Story 3

- [x] T011 [US3] Add `PUT /v1/businesses/{id}` endpoint to `src/main/java/com/proximityservice/controller/BusinessController.java` — @Valid BusinessUpdateRequest 수신, BusinessService.update() 호출, 200 OK + BusinessDetailResponse 반환

**Checkpoint**: 수정 동작 확인 — 좌표 변경 유무에 따른 Redis 갱신 차이 검증

---

## Phase 6: User Story 4 — 사업장 삭제 (Priority: P4)

**Goal**: 사업장을 삭제하고 즉시 검색 결과에서 제외한다

**Independent Test**: `DELETE /v1/businesses/{id}` 후 `GET`으로 404 확인, 주변 검색에서 제외 확인

### Implementation for User Story 4

- [x] T012 [US4] Add `DELETE /v1/businesses/{id}` endpoint to `src/main/java/com/proximityservice/controller/BusinessController.java` — BusinessService.delete() 호출, 204 No Content 반환

**Checkpoint**: 삭제 후 조회 시 404, 주변 검색에서 제외 확인

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 전체 API에 걸친 개선사항

- [x] T013 [P] Add Category enum validation error message to GlobalExceptionHandler — 잘못된 카테고리 입력 시 허용 목록을 포함한 400 응답
- [x] T014 Run quickstart.md validation — 등록→조회→수정→삭제→검색 전체 흐름 수동 검증

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 즉시 시작 가능 — T001, T002, T003 모두 병렬 실행 가능
- **Phase 2 (Foundational)**: Phase 1 완료 후 시작 — T004는 T001(Category enum) 의존, T005~T007은 병렬, T008은 T004~T007 모두 완료 후
- **Phase 3~6 (User Stories)**: Phase 2 완료 후 시작 — 모든 엔드포인트가 같은 Controller 파일이므로 순차 실행 권장
- **Phase 7 (Polish)**: Phase 6 완료 후 시작

### User Story Dependencies

- **US1 (상세 조회)**: Phase 2 완료 후 즉시 시작 가능 — 다른 US에 의존 없음
- **US2 (등록)**: Phase 2 완료 후 시작 가능 — US1과 같은 Controller 파일이므로 US1 후 순차
- **US3 (수정)**: US2 후 순차 — 같은 Controller 파일
- **US4 (삭제)**: US3 후 순차 — 같은 Controller 파일

### Within Each User Story

- 서비스 로직은 T008(BusinessService)에서 일괄 구현
- 각 US 태스크는 Controller 엔드포인트 추가만 담당
- 엔드포인트 추가 후 즉시 해당 US의 독립 테스트 가능

### Parallel Opportunities

```
Phase 1: T001 ║ T002 ║ T003  (모두 병렬)
Phase 2: T005 ║ T006 ║ T007  (DTO 병렬), T004 단독, T008 마지막
Phase 3~6: T009 → T010 → T011 → T012 (순차 — 같은 파일)
Phase 7: T013 ║ T014 (병렬)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001~T003)
2. Phase 2: Foundational (T004~T008)
3. Phase 3: User Story 1 (T009)
4. **STOP and VALIDATE**: `GET /v1/businesses/{id}`로 상세 조회 동작 확인
5. 기존 시드 API로 등록한 데이터를 조회할 수 있으면 MVP 완성

### Incremental Delivery

1. Setup + Foundational → 기반 완료
2. + US1 (조회) → 시드 데이터 상세 조회 가능
3. + US2 (등록) → 새 사업장 등록 + 검색 노출
4. + US3 (수정) → 정보 수정 + 좌표 변경 시 인덱스 갱신
5. + US4 (삭제) → 삭제 + 검색 제외
6. Polish → 에러 메시지 개선 + 전체 흐름 검증

---

## Notes

- 모든 Controller 엔드포인트는 `BusinessController.java` 단일 파일에 추가
- 비즈니스 로직은 `BusinessService.java`에 집중 (T008에서 일괄 구현)
- Phase 1 기존 코드(BusinessRepository, BusinessGeoRepository)를 그대로 재활용
- 기존 seed API (`POST /v1/businesses/seed`)는 변경하지 않음
