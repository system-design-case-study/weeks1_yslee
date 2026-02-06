# Tasks: 주변 검색 API (Nearby Search API)

**Input**: Design documents from `/specs/001-nearby-search-api/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/nearby-search-api.yaml

**Tests**: spec.md에서 Testcontainers 기반 통합 테스트를 명시하므로 테스트 태스크 포함.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/java/com/proximityservice/`, `src/test/java/com/proximityservice/` at repository root

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Spring Boot 프로젝트 초기화 및 인프라 설정

- [x] T001 Create `build.gradle.kts` with Spring Boot 3.4.x, Spring Data Redis, Spring Data JPA, MySQL Connector, Testcontainers dependencies
- [x] T002 Create `docker-compose.yml` with MySQL 8.0 and Redis 7 services
- [x] T003 [P] Create `src/main/resources/application.yml` with MySQL, Redis, JPA configuration
- [x] T004 [P] Create `src/main/java/com/proximityservice/ProximityServiceApplication.java` with `@SpringBootApplication`
- [x] T005 [P] Create `settings.gradle.kts` with project name

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 User Story가 공유하는 엔티티, Repository, 설정

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T006 Create Business JPA entity in `src/main/java/com/proximityservice/domain/Business.java` — UUID PK, name, address, latitude, longitude, category, phone, hours, created_at, updated_at per data-model.md
- [x] T007 Create BusinessRepository (JPA) in `src/main/java/com/proximityservice/repository/BusinessRepository.java` — `findAllById(List<String>)` 메서드 포함
- [x] T008 Create RedisConfig with `StringRedisTemplate` bean in `src/main/java/com/proximityservice/config/RedisConfig.java`
- [x] T009 Create BusinessGeoRepository in `src/main/java/com/proximityservice/repository/BusinessGeoRepository.java` — `GeoOperations`를 사용한 `add(id, lng, lat)`, `searchNearby(lng, lat, radius)` 메서드
- [x] T010 [P] Create ErrorResponse DTO in `src/main/java/com/proximityservice/dto/ErrorResponse.java` — error, message, details fields per OpenAPI spec
- [x] T011 [P] Create InvalidParameterException in `src/main/java/com/proximityservice/exception/InvalidParameterException.java`
- [x] T012 Create GlobalExceptionHandler in `src/main/java/com/proximityservice/exception/GlobalExceptionHandler.java` — `@ControllerAdvice`, InvalidParameterException → 400 ErrorResponse 매핑

**Checkpoint**: Foundation ready - Entity, Repository, Redis GEO, 에러 처리 인프라 완료

---

## Phase 3: User Story 1 — 반경 내 사업장 검색 (Priority: P1) 🎯 MVP

**Goal**: 위도/경도/반경으로 주변 사업장을 검색하여 거리순으로 반환한다.

**Independent Test**: `curl "http://localhost:8080/v1/search/nearby?latitude=37.5012&longitude=127.0396&radius=1000"` 호출 시 반경 내 사업장이 거리순으로 반환되는지 확인

### Implementation for User Story 1

- [x] T013 [P] [US1] Create BusinessSearchResult DTO in `src/main/java/com/proximityservice/dto/BusinessSearchResult.java` — id, name, address, latitude, longitude, distance_m, category fields
- [x] T014 [P] [US1] Create NearbySearchResponse DTO in `src/main/java/com/proximityservice/dto/NearbySearchResponse.java` — total, businesses(List), message fields
- [x] T015 [US1] Implement NearbySearchService in `src/main/java/com/proximityservice/service/NearbySearchService.java` — Redis GEOSEARCH → MySQL findAllById → BusinessSearchResult 매핑, 거리순 정렬
- [x] T016 [US1] Implement NearbySearchController `GET /v1/search/nearby` in `src/main/java/com/proximityservice/controller/NearbySearchController.java` — latitude, longitude, radius(default 5000) 파라미터 바인딩

### Data Seeding (US1에서 테스트하려면 데이터가 필요)

- [x] T017 [P] [US1] Create BusinessSeedRequest DTO in `src/main/java/com/proximityservice/dto/BusinessSeedRequest.java` — name, address, latitude, longitude, category, phone, hours fields with validation
- [x] T018 [US1] Implement BusinessSeedService in `src/main/java/com/proximityservice/service/BusinessSeedService.java` — MySQL INSERT + Redis GEOADD 동시 수행
- [x] T019 [US1] Implement BusinessSeedController `POST /v1/businesses/seed` in `src/main/java/com/proximityservice/controller/BusinessSeedController.java` — 배열 입력, 201 응답 with created_count

### Test for User Story 1

- [x] T020 [US1] Create BusinessGeoRepositoryTest in `src/test/java/com/proximityservice/repository/BusinessGeoRepositoryTest.java` — Testcontainers Redis, GEOADD/GEOSEARCH 동작 검증
- [x] T021 [US1] Create NearbySearchServiceTest in `src/test/java/com/proximityservice/service/NearbySearchServiceTest.java` — 검색 결과 거리순 정렬, 반경 내 결과만 포함 검증
- [x] T022 [US1] Create NearbySearchControllerTest in `src/test/java/com/proximityservice/controller/NearbySearchControllerTest.java` — MockMvc로 GET /v1/search/nearby 200 응답, 기본 반경 5000m 적용 검증

**Checkpoint**: 검색 API + 시딩 API 동작. curl로 데이터 시딩 후 검색 가능. MVP 완료.

---

## Phase 4: User Story 2 — 검색 결과 없음 처리 (Priority: P2)

**Goal**: 반경 내 사업장이 없을 때 빈 결과와 반경 확대 안내 메시지를 반환한다.

**Independent Test**: 사업장이 없는 좌표로 검색하여 `total: 0`, `message: "검색 결과가 없습니다..."` 반환 확인

### Implementation for User Story 2

- [x] T023 [US2] Update NearbySearchService in `src/main/java/com/proximityservice/service/NearbySearchService.java` — 검색 결과 0건 시 반경 확대 안내 message 필드 설정
- [x] T024 [US2] Create NearbySearchControllerTest empty result case in `src/test/java/com/proximityservice/controller/NearbySearchControllerTest.java` — 결과 0건 시 message 포함 검증

**Checkpoint**: 결과 없음 시 안내 메시지 반환. US1과 독립적으로 검증 가능.

---

## Phase 5: User Story 3 — 잘못된 입력 처리 (Priority: P3)

**Goal**: 유효 범위를 벗어난 latitude/longitude/radius에 대해 에러 응답과 유효 범위 안내를 반환한다.

**Independent Test**: `latitude=91`로 검색 시 400 에러 + "위도는 -90에서 90 사이여야 합니다" 메시지 확인

### Implementation for User Story 3

- [x] T025 [US3] Add input validation to NearbySearchController in `src/main/java/com/proximityservice/controller/NearbySearchController.java` — latitude(-90~90), longitude(-180~180), radius(1~20000) 범위 검증, InvalidParameterException throw
- [x] T026 [US3] Update GlobalExceptionHandler in `src/main/java/com/proximityservice/exception/GlobalExceptionHandler.java` — InvalidParameterException 시 유효 범위를 details에 포함 (e.g., `{"valid_range": "-90 ~ 90"}`)
- [x] T027 [US3] Create validation error test cases in `src/test/java/com/proximityservice/controller/NearbySearchControllerTest.java` — 위도 91, 경도 200, 반경 50000 각각 400 에러 응답 검증

**Checkpoint**: 모든 잘못된 입력에 대해 친절한 에러 메시지 반환. 3개 User Story 모두 완료.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 전체 스토리에 걸친 개선

- [ ] T028 [P] Validate quickstart.md scenarios — docker compose up, 시딩, 검색, 에러 케이스 순서대로 수행하여 문서와 실제 동작 일치 확인
- [ ] T029 [P] Verify edge cases — 반경 경계선, (0,0) 좌표, 1m 반경, 동일 위치 사업장 동작 확인

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational — 🎯 MVP
- **User Story 2 (Phase 4)**: Depends on Phase 3 (US1의 NearbySearchService에 로직 추가)
- **User Story 3 (Phase 5)**: Depends on Phase 2 (Foundational만 필요, US1과 병렬 가능)
- **Polish (Phase 6)**: Depends on all user stories complete

### User Story Dependencies

- **User Story 1 (P1)**: Foundational 완료 후 시작. 독립 테스트 가능.
- **User Story 2 (P2)**: US1의 NearbySearchService 위에 빈 결과 처리 추가. US1 완료 후 시작.
- **User Story 3 (P3)**: Foundational의 예외 처리 인프라 위에 검증 추가. US1과 병렬 가능하나, Controller가 공유되므로 순차 권장.

### Within Each User Story

- DTO/Model → Service → Controller → Test 순서
- 시딩 API는 US1 검증에 필요하므로 US1에 포함

### Parallel Opportunities

- T003, T004, T005는 병렬 가능 (독립 파일)
- T010, T011은 병렬 가능 (독립 DTO/예외 클래스)
- T013, T014는 병렬 가능 (독립 DTO 파일)
- T017은 T013, T014와 병렬 가능 (독립 DTO 파일)

---

## Parallel Example: User Story 1

```bash
# Launch DTO creation in parallel:
Task: "Create BusinessSearchResult DTO in src/main/java/com/proximityservice/dto/BusinessSearchResult.java"
Task: "Create NearbySearchResponse DTO in src/main/java/com/proximityservice/dto/NearbySearchResponse.java"
Task: "Create BusinessSeedRequest DTO in src/main/java/com/proximityservice/dto/BusinessSeedRequest.java"

# Then sequentially:
Task: "Implement NearbySearchService" (depends on DTOs + Repository)
Task: "Implement NearbySearchController" (depends on Service)
Task: "Implement BusinessSeedService" (depends on DTOs + Repository)
Task: "Implement BusinessSeedController" (depends on SeedService)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (build.gradle, docker-compose, application.yml)
2. Complete Phase 2: Foundational (Entity, Repository, Redis GEO, 에러 처리)
3. Complete Phase 3: User Story 1 (검색 + 시딩 API)
4. **STOP and VALIDATE**: quickstart.md 시나리오대로 시딩 → 검색 동작 확인
5. MVP 완료

### Incremental Delivery

1. Setup + Foundational → 인프라 준비
2. User Story 1 → 시딩 + 검색 동작 → **MVP!**
3. User Story 2 → 빈 결과 안내 추가
4. User Story 3 → 입력 검증 + 에러 메시지 추가
5. Polish → quickstart 검증, edge case 확인

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each phase or logical group
- Stop at any checkpoint to validate story independently
