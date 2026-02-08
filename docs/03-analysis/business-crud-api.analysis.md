# Business CRUD API Analysis Report

> **Analysis Type**: Gap Analysis (Design vs Implementation)
>
> **Project**: proximity-service
> **Feature**: 002-business-crud-api
> **Date**: 2026-02-08
> **Design Docs**: `specs/002-business-crud-api/` (spec.md, plan.md, data-model.md, contracts/business-api.yaml, tasks.md)

---

## 1. Analysis Overview

### 1.1 Analysis Purpose

Phase 2 (business-crud-api) 구현 완료 후, 설계 문서(spec, data-model, OpenAPI contract)와 실제 구현 코드 간의 일치도를 검증한다.

### 1.2 Analysis Scope

- **Design Documents**: `specs/002-business-crud-api/` (5개 파일)
- **Implementation Path**: `src/main/java/com/proximityservice/` (11개 파일)
- **FR Count**: 10개 (FR-001 ~ FR-010)
- **Endpoint Count**: 4개 (GET, POST, PUT, DELETE)

---

## 2. Functional Requirements Gap Analysis (FR-001 ~ FR-010)

### 2.1 FR-by-FR Verification

| FR | Description | Implemented | Correct | Notes |
|----|-------------|:-----------:|:-------:|-------|
| FR-001 | 사업장 ID로 상세 정보 조회 | YES | YES | `BusinessController.getBusiness()` -> 200 OK + `BusinessDetailResponse` |
| FR-002 | 필수/선택 정보로 신규 사업장 등록 | YES | YES | `BusinessCreateRequest` record + `@Valid` 검증 |
| FR-003 | UUID 자동 발급 | YES | YES | `Business` 생성자에서 `UUID.randomUUID().toString()` |
| FR-004 | 등록/삭제 시 Redis 즉시 반영 | YES | YES | `create()`: GEOADD, `delete()`: ZREM |
| FR-005 | 수정 시 좌표 변경 시에만 Redis 갱신 | YES | YES | `update()`: `coordinatesChanged` 플래그로 조건부 ZREM+GEOADD |
| FR-006 | 기존 사업장 모든 필드 수정 | YES | YES | `Business.update()` 메서드로 전 필드 갱신 |
| FR-007 | 사업장 삭제 (Hard Delete) | YES | YES | `businessRepository.delete()` + `geoRepository.remove()` |
| FR-008 | 입력값 검증 | YES | PARTIAL | 좌표 범위, 필수 필드, 카테고리 유효성은 동작하지만 **문자열 길이 검증 누락** (아래 상세) |
| FR-009 | 존재하지 않는 사업장 404 응답 | YES | YES | `BusinessNotFoundException` -> `GlobalExceptionHandler` -> 404 |
| FR-010 | 카테고리 사전 정의 값 제한 | YES | YES | `Category.fromValue()` 검증 + `IllegalArgumentException` -> 400 |

### 2.2 FR-008 상세 (입력값 검증)

| 검증 항목 | 설계 | 구현 | Status |
|-----------|------|------|:------:|
| 좌표 범위 (latitude -90~90) | `@DecimalMin("-90") @DecimalMax("90")` | `@DecimalMin("-90") @DecimalMax("90")` | MATCH |
| 좌표 범위 (longitude -180~180) | `@DecimalMin("-180") @DecimalMax("180")` | `@DecimalMin("-180") @DecimalMax("180")` | MATCH |
| 필수 필드 (name) | `@NotBlank` | `@NotBlank` | MATCH |
| 필수 필드 (latitude, longitude) | `@NotNull` | `@NotNull` | MATCH |
| 필수 필드 (category) | `@NotBlank` | `@NotBlank` | MATCH |
| 카테고리 유효성 | `Category.fromValue()` | `Category.fromValue()` | MATCH |
| name 최대 길이 | 255자 (data-model + OpenAPI) | **검증 없음** (`@Size` 미적용) | GAP |
| address 최대 길이 | 500자 (data-model + OpenAPI) | **검증 없음** | GAP |
| phone 최대 길이 | 20자 (data-model + OpenAPI) | **검증 없음** | GAP |
| hours 최대 길이 | 100자 (data-model + OpenAPI) | **검증 없음** | GAP |

**Gap 심각도**: Low -- JPA `@Column(length=N)` 제약이 DB 레벨에서 잘라내지만, API 레벨에서 명확한 400 응답을 주지 못한다. OpenAPI 계약에 `maxLength`가 명시되어 있으므로 DTO에 `@Size(max=N)` 추가가 권장된다.

---

## 3. API Contract Compliance (OpenAPI vs Implementation)

### 3.1 Endpoints

| OpenAPI | Implementation | Method | Status |
|---------|---------------|:------:|:------:|
| `POST /v1/businesses` | `@PostMapping` on `/v1/businesses` | POST | MATCH |
| `GET /v1/businesses/{id}` | `@GetMapping("/{id}")` | GET | MATCH |
| `PUT /v1/businesses/{id}` | `@PutMapping("/{id}")` | PUT | MATCH |
| `DELETE /v1/businesses/{id}` | `@DeleteMapping("/{id}")` | DELETE | MATCH |

### 3.2 HTTP Status Codes

| Endpoint | OpenAPI | Implementation | Status |
|----------|---------|---------------|:------:|
| POST success | 201 Created | `HttpStatus.CREATED` | MATCH |
| GET success | 200 OK | `ResponseEntity.ok()` | MATCH |
| PUT success | 200 OK | `ResponseEntity.ok()` | MATCH |
| DELETE success | 204 No Content | `ResponseEntity.noContent()` | MATCH |
| Not found | 404 | `BusinessNotFoundException` -> 404 | MATCH |
| Validation fail | 400 | `MethodArgumentNotValidException` -> 400 | MATCH |

### 3.3 Request Schema (BusinessCreateRequest)

| OpenAPI Field | Required | DTO Field | Validation | Status |
|---------------|:--------:|-----------|-----------|:------:|
| name | YES | `@NotBlank String name` | Present | MATCH |
| address | NO | `String address` | Optional | MATCH |
| latitude | YES | `@NotNull @DecimalMin/Max Double latitude` | Present | MATCH |
| longitude | YES | `@NotNull @DecimalMin/Max Double longitude` | Present | MATCH |
| category | YES | `@NotBlank String category` | Present | MATCH |
| phone | NO | `String phone` | Optional | MATCH |
| hours | NO | `String hours` | Optional | MATCH |

### 3.4 Request Schema (BusinessUpdateRequest)

| OpenAPI Field | Required | DTO Field | Validation | Status |
|---------------|:--------:|-----------|-----------|:------:|
| name | YES | `@NotBlank String name` | Present | MATCH |
| address | NO (not in required) | `String address` | Optional | MATCH |
| latitude | YES | `@NotNull @DecimalMin/Max Double latitude` | Present | MATCH |
| longitude | YES | `@NotNull @DecimalMin/Max Double longitude` | Present | MATCH |
| category | YES | `@NotBlank String category` | Present | MATCH |
| phone | NO | `String phone` | Optional | MATCH |
| hours | NO | `String hours` | Optional | MATCH |

### 3.5 Response Schema (BusinessDetailResponse)

| OpenAPI Field | Implementation Field | JSON Name | Status |
|---------------|---------------------|-----------|:------:|
| id (uuid) | `String id` | `id` | MATCH |
| name | `String name` | `name` | MATCH |
| address | `String address` | `address` | MATCH |
| latitude (double) | `Double latitude` | `latitude` | MATCH |
| longitude (double) | `Double longitude` | `longitude` | MATCH |
| category | `String category` | `category` | MATCH |
| phone | `String phone` | `phone` | MATCH |
| hours | `String hours` | `hours` | MATCH |
| created_at (date-time) | `LocalDateTime createdAt` | `created_at` (`@JsonProperty`) | MATCH |
| updated_at (date-time) | `LocalDateTime updatedAt` | `updated_at` (`@JsonProperty`) | MATCH |

### 3.6 Error Response Schema

| OpenAPI Field | Implementation | Status |
|---------------|---------------|:------:|
| error (string) | `String error` | MATCH |
| message (string) | `String message` | MATCH |
| details (object) | `Map<String, Object> details` | MATCH |

---

## 4. Data Model Compliance

### 4.1 Entity Fields (data-model.md vs Business.java)

| Field | Design Type | Design Constraint | Implementation | Status |
|-------|-------------|-------------------|---------------|:------:|
| id | String (UUID) | PK, 36 chars | `@Id @Column(length=36)` | MATCH |
| name | String | NOT NULL, 255 chars | `@Column(nullable=false, length=255)` | MATCH |
| address | String | NOT NULL, 500 chars | `@Column(nullable=false, length=500)` | MATCH |
| latitude | Double | NOT NULL, DECIMAL(10,7) | `@Column(nullable=false, columnDefinition="DECIMAL(10,7)")` | MATCH |
| longitude | Double | NOT NULL, DECIMAL(10,7) | `@Column(nullable=false, columnDefinition="DECIMAL(10,7)")` | MATCH |
| category | String | NOT NULL, 50 chars | `@Column(nullable=false, length=50)` | MATCH |
| phone | String | nullable, 20 chars | `@Column(length=20)` | MATCH |
| hours | String | nullable, 100 chars | `@Column(length=100)` | MATCH |
| created_at | LocalDateTime | NOT NULL, auto | `@CreationTimestamp @Column(nullable=false, updatable=false)` | MATCH |
| updated_at | LocalDateTime | NOT NULL, auto | `@UpdateTimestamp @Column(nullable=false)` | MATCH |

### 4.2 Business.update() Method

| Design | Implementation | Status |
|--------|---------------|:------:|
| 모든 필드를 갱신하고 좌표 변경 여부(boolean) 반환 | `public boolean update(...)` -- 7개 필드 갱신, `coordinatesChanged` 반환 | MATCH |
| category 파라미터는 String으로 받음 | `String category` parameter | MATCH |
| Category enum 검증은 서비스 레이어에서 수행 | `BusinessService.update()`에서 `Category.fromValue()` 호출 | MATCH |

### 4.3 Category Enum (data-model.md vs Category.java)

| Design Value | Enum Constant | `getValue()` | Status |
|-------------|---------------|-------------|:------:|
| KOREAN_FOOD | `KOREAN_FOOD` | `korean_food` | MATCH |
| CHINESE_FOOD | `CHINESE_FOOD` | `chinese_food` | MATCH |
| JAPANESE_FOOD | `JAPANESE_FOOD` | `japanese_food` | MATCH |
| WESTERN_FOOD | `WESTERN_FOOD` | `western_food` | MATCH |
| CAFE | `CAFE` | `cafe` | MATCH |
| BAR | `BAR` | `bar` | MATCH |
| CONVENIENCE | `CONVENIENCE` | `convenience` | MATCH |
| PHARMACY | `PHARMACY` | `pharmacy` | MATCH |
| HAIR_SALON | `HAIR_SALON` | `hair_salon` | MATCH |
| GYM | `GYM` | `gym` | MATCH |

`fromValue(String)` static method: MATCH (data-model.md 요구사항 충족)

### 4.4 Validation Rules (data-model.md vs DTO)

| Field | Design Rule | Implementation | Status |
|-------|------------|---------------|:------:|
| name | 필수, 1~255자, 공백만 불가 | `@NotBlank` (공백만 불가 포함), **`@Size(max=255)` 누락** | PARTIAL |
| address | 선택, 최대 500자 | Optional, **`@Size(max=500)` 누락** | PARTIAL |
| latitude | 필수, -90.0 ~ 90.0 | `@NotNull @DecimalMin("-90") @DecimalMax("90")` | MATCH |
| longitude | 필수, -180.0 ~ 180.0 | `@NotNull @DecimalMin("-180") @DecimalMax("180")` | MATCH |
| category | 필수, Category enum 값만 | `@NotBlank` + `Category.fromValue()` in service | MATCH |
| phone | 선택, 최대 20자 | Optional, **`@Size(max=20)` 누락** | PARTIAL |
| hours | 선택, 최대 100자 | Optional, **`@Size(max=100)` 누락** | PARTIAL |

---

## 5. Redis Sync Rules Compliance (ADR-4)

### 5.1 Sync Strategy (data-model.md vs BusinessService.java)

| CRUD | Design (MySQL) | Design (Redis) | Implementation (MySQL) | Implementation (Redis) | Status |
|------|---------------|----------------|----------------------|----------------------|:------:|
| CREATE | INSERT | GEOADD | `businessRepository.save()` | `geoRepository.add()` | MATCH |
| READ | SELECT (PK) | - | `businessRepository.findById()` | (none) | MATCH |
| UPDATE (no coord change) | UPDATE | - | JPA dirty checking | (skipped) | MATCH |
| UPDATE (coord changed) | UPDATE | ZREM + GEOADD | JPA dirty checking | `geoRepository.remove()` + `geoRepository.add()` | MATCH |
| DELETE | DELETE | ZREM | `businessRepository.delete()` | `geoRepository.remove()` | MATCH |

### 5.2 Transaction Boundary

| Concern | Design | Implementation | Status |
|---------|--------|---------------|:------:|
| create 트랜잭션 | MySQL+Redis 동기 처리 | `@Transactional` on `create()` | MATCH |
| update 트랜잭션 | MySQL+Redis 동기 처리 | `@Transactional` on `update()` | MATCH |
| delete 트랜잭션 | MySQL+Redis 동기 처리 | `@Transactional` on `delete()` | MATCH |
| read 트랜잭션 | read-only | `@Transactional(readOnly=true)` on `getById()` | MATCH |

---

## 6. Error Handling Compliance

### 6.1 Exception Handlers

| Scenario | Design (spec.md) | Implementation | Status |
|----------|------------------|---------------|:------:|
| 사업장 미존재 (조회/수정/삭제) | 404 Not Found + 오류 메시지 | `BusinessNotFoundException` -> 404 + `ErrorResponse` | MATCH |
| 필수 필드 누락 | 400 Bad Request + 누락 필드 명시 | `MethodArgumentNotValidException` -> 400 + field info | MATCH |
| 좌표 범위 초과 | 400 Bad Request + 유효 범위 안내 | `@DecimalMin/@DecimalMax` -> `MethodArgumentNotValidException` -> 400 | MATCH |
| 잘못된 카테고리 | 400 Bad Request + 허용 목록 안내 | `IllegalArgumentException` -> 400 + allowed values | MATCH |

### 6.2 Unused Import

`GlobalExceptionHandler.java` line 10: `MethodArgumentTypeMismatchException`이 import되어 있으나 실제 핸들러가 없다. 코드 정리가 권장된다.

---

## 7. Code Quality Notes

### 7.1 Code Smells

| Type | File | Description | Severity |
|------|------|-------------|----------|
| Unused import | `GlobalExceptionHandler.java:10` | `MethodArgumentTypeMismatchException` imported but not used | Low |

### 7.2 Architecture Compliance

| Concern | Expected | Actual | Status |
|---------|----------|--------|:------:|
| Controller -> Service dependency | Controller calls Service only | `BusinessController` -> `BusinessService` | MATCH |
| Service -> Repository dependency | Service calls Repository | `BusinessService` -> `BusinessRepository`, `BusinessGeoRepository` | MATCH |
| Domain independence | No outward dependency | `Business.java` only uses JPA/Lombok annotations | MATCH |
| DTO -> Domain conversion | Static factory method | `BusinessDetailResponse.from(Business)` | MATCH |

---

## 8. Overall Score

### 8.1 Category Scores

| Category | Items | Matched | Gaps | Score | Status |
|----------|:-----:|:-------:|:----:|:-----:|:------:|
| Functional Requirements (FR-001~010) | 10 | 9 | 1 (partial) | 95% | PASS |
| API Contract (endpoints, status, schemas) | 30 | 30 | 0 | 100% | PASS |
| Data Model (entity fields, types, constraints) | 10 | 10 | 0 | 100% | PASS |
| Validation Rules | 7 | 3 | 4 (partial) | 71% | WARN |
| Redis Sync (ADR-4) | 5 | 5 | 0 | 100% | PASS |
| Error Handling | 4 | 4 | 0 | 100% | PASS |

### 8.2 Overall Match Rate

```
+---------------------------------------------+
|  Overall Match Rate: 95%                    |
+---------------------------------------------+
|  MATCH:           61 items (95%)            |
|  PARTIAL:          4 items (5%)             |
|  MISSING:          0 items (0%)             |
|  ADDED (not in design): 0 items (0%)       |
+---------------------------------------------+
```

---

## 9. Gap Summary

### 9.1 Gaps Found

| # | Type | Item | Design Location | Implementation Location | Impact | Description |
|---|------|------|-----------------|------------------------|--------|-------------|
| 1 | PARTIAL | name maxLength validation | data-model.md:47, business-api.yaml:121 | `BusinessCreateRequest.java:9` | Low | `@Size(max=255)` 미적용. DB `@Column(length=255)`로 인한 잘림은 발생하지만 API 레벨 400 응답이 아닌 DB 에러로 나타남 |
| 2 | PARTIAL | address maxLength validation | data-model.md:48, business-api.yaml:125 | `BusinessCreateRequest.java:10` | Low | `@Size(max=500)` 미적용 |
| 3 | PARTIAL | phone maxLength validation | data-model.md:52, business-api.yaml:147 | `BusinessCreateRequest.java:14` | Low | `@Size(max=20)` 미적용 |
| 4 | PARTIAL | hours maxLength validation | data-model.md:53, business-api.yaml:150 | `BusinessCreateRequest.java:15` | Low | `@Size(max=100)` 미적용 |

### 9.2 Code Quality Issues

| # | Type | File | Line | Description |
|---|------|------|------|-------------|
| 1 | Unused import | `GlobalExceptionHandler.java` | 10 | `MethodArgumentTypeMismatchException` imported but no handler defined |

---

## 10. Recommended Actions

### 10.1 Immediate (Gap resolution)

| Priority | Action | Files | Description |
|----------|--------|-------|-------------|
| 1 | Add `@Size` annotations | `BusinessCreateRequest.java`, `BusinessUpdateRequest.java` | `@Size(max=255)` on name, `@Size(max=500)` on address, `@Size(max=20)` on phone, `@Size(max=100)` on hours |
| 2 | Remove unused import | `GlobalExceptionHandler.java` | Remove `MethodArgumentTypeMismatchException` import (line 10) |

### 10.2 Not Required

- API endpoint changes: None needed (100% match)
- Data model changes: None needed (100% match)
- Redis sync changes: None needed (100% match)
- Error handler changes: None needed (100% match)
- Design document updates: None needed

---

## 11. Conclusion

Business CRUD API 구현은 설계 문서와 **95% 일치**한다. 모든 10개 FR이 구현되어 있고, 4개 API 엔드포인트, HTTP 상태 코드, 요청/응답 스키마, Redis 동기화 전략(ADR-4), 에러 처리가 모두 설계대로 동작한다.

유일한 Gap은 DTO 레벨의 문자열 길이 검증(`@Size` annotation) 4건으로, DB 컬럼 제약으로 인한 데이터 무결성은 보장되지만 API 계약(OpenAPI `maxLength`)에 명시된 클라이언트 친화적 400 응답이 누락되어 있다. 이 Gap은 Low 심각도이며, `@Size` annotation 추가만으로 해결 가능하다.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-02-08 | Initial gap analysis | gap-detector |
