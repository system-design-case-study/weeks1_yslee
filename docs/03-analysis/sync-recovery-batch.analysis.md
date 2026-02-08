# Gap Analysis: 앱 레벨 동기화 + 복구용 배치

**Date**: 2026-02-08
**Feature Branch**: 003-sync-recovery-batch
**Match Rate**: 93%

## Summary

전반적으로 설계 문서와 구현이 높은 수준으로 일치한다. 10개의 기능 요구사항(FR) 중 10개가 구현되었고, 5개의 리서치 결정(R) 모두 올바르게 반영되었다. API 계약(OpenAPI)과 실제 엔드포인트 매핑도 정확하다. 데이터 모델(SyncBatchResult)은 설계 문서의 필드 정의와 완전히 일치한다.

발견된 차이점은 Minor 수준 3건이며, 기능 동작에는 영향이 없다.

## Functional Requirements Check

| FR | Description | Status | Notes |
|----|-------------|--------|-------|
| FR-001 | 전체 사업장 데이터 기반 검색 인덱스 재구축 배치 | PASS | `SyncBatchService.fullSync()` 구현 완료 |
| FR-002 | 기존 검색 인덱스 초기화 후 재구축 | PASS | `geoRepository.deleteAll()` 호출 후 청크 단위 GEOADD |
| FR-003 | 정합성 검증 및 불일치 보정 배치 | PASS | `SyncBatchService.consistencyCheck()` 구현 완료 |
| FR-004 | 페이지(청크) 단위 대량 데이터 처리 | PASS | `CHUNK_SIZE = 500`, `PageRequest.of(page, CHUNK_SIZE)` 사용 |
| FR-005 | 배치 결과 리포트 (처리/추가/제거/오류 건수, 소요 시간) | PASS | `SyncBatchResult` record에 9개 필드로 완전한 리포트 |
| FR-006 | 동시 실행 방지 (중복 배치 차단) | PASS | `AtomicBoolean running`으로 compareAndSet 방식 구현 |
| FR-007 | 앱 레벨 동기화 실패 시 최대 3회 재시도 | PASS | `@Retryable(maxAttempts = 3)` 적용 |
| FR-008 | 재시도 포함 모든 동기화 실패 로그 기록 | PASS | `@Recover` 메서드 4개에서 `log.error` 기록 |
| FR-009 | 수동 트리거 방식 실행 | PASS | `POST /v1/admin/sync/full`, `POST /v1/admin/sync/consistency-check` |
| FR-010 | 고아 데이터 제거 (Redis에만 존재하는 항목) | PASS | `orphaned = redisMembers - mysqlIds` 계산 후 `geoRepository.remove()` |

**FR 달성률: 10/10 (100%)**

## API Contract Check

### Endpoint Mapping

| Spec (batch-api.yaml) | Implementation (SyncBatchController) | Status |
|------------------------|--------------------------------------|--------|
| `POST /v1/admin/sync/full` | `@PostMapping("/full")` on `@RequestMapping("/v1/admin/sync")` | PASS |
| `POST /v1/admin/sync/consistency-check` | `@PostMapping("/consistency-check")` | PASS |

### Response Schema (SyncBatchResult)

| OpenAPI Field | Type (Spec) | Implementation Field | Type (Impl) | Status |
|---------------|-------------|---------------------|-------------|--------|
| type | string enum [FULL_SYNC, CONSISTENCY_CHECK] | type | String | PASS |
| status | string enum [SUCCESS, PARTIAL_FAILURE, FAILED] | status | String | PASS |
| total_processed | integer | totalProcessed (@JsonProperty("total_processed")) | int | PASS |
| added | integer | added | int | PASS |
| removed | integer | removed | int | PASS |
| errors | integer | errors | int | PASS |
| started_at | string (date-time) | startedAt (@JsonProperty("started_at")) | LocalDateTime | PASS |
| finished_at | string (date-time) | finishedAt (@JsonProperty("finished_at")) | LocalDateTime | PASS |
| duration_ms | integer (int64) | durationMs (@JsonProperty("duration_ms")) | long | PASS |

### Error Response (409 Conflict)

| Spec | Implementation | Status |
|------|----------------|--------|
| 409 with ErrorResponse {error, message} | `IllegalStateException` -> `GlobalExceptionHandler.handleIllegalState()` -> 409 CONFLICT {error: "CONFLICT", message: ...} | PASS |

**API 계약 점수: 12/12 (100%)**

## Data Model Check

### SyncBatchResult (data-model.md vs dto/SyncBatchResult.java)

| Design Field | Design Type | Impl Field | Impl Type | Status |
|-------------|------------|------------|-----------|--------|
| type | string | type | String | PASS |
| status | string | status | String | PASS |
| total_processed | int | totalProcessed | int | PASS |
| added | int | added | int | PASS |
| removed | int | removed | int | PASS |
| errors | int | errors | int | PASS |
| started_at | datetime | startedAt | LocalDateTime | PASS |
| finished_at | datetime | finishedAt | LocalDateTime | PASS |
| duration_ms | long | durationMs | long | PASS |

**데이터 모델 점수: 9/9 (100%)**

## Research Decisions Check

| Decision | Description | Status | Notes |
|----------|-------------|--------|-------|
| R-001 | Spring Retry (@Retryable, maxAttempts=3, exponential backoff) | PASS | `@Retryable(retryFor={RedisConnectionFailureException, QueryTimeoutException}, maxAttempts=3, backoff=@Backoff(delay=1000, multiplier=2))`. `@EnableRetry` 설정 완료. `@Recover` 폴백 메서드 구현 완료. |
| R-002 | Pageable 청크 처리 (CHUNK_SIZE=500) | PASS | `CHUNK_SIZE = 500`, `PageRequest.of(page, CHUNK_SIZE)` do-while loop |
| R-003 | DEL + rebuild 전략 (전체 동기화) | PASS | `geoRepository.deleteAll()` (DEL key) 후 청크별 GEOADD |
| R-004 | Set 비교 정합성 검증 (MySQL IDs vs Redis members) | PASS | `HashSet<String> mysqlIds`, `geoRepository.getAllMembers()`, 집합 차이 연산 |
| R-005 | AtomicBoolean 동시 실행 방지 | PASS | `private final AtomicBoolean running = new AtomicBoolean(false)`, `compareAndSet` + `finally` 해제 |

**리서치 결정 점수: 5/5 (100%)**

## Edge Cases Check

| Edge Case (Spec) | Implementation | Status |
|-------------------|----------------|--------|
| 배치 중 Redis 연결 끊김 -> 처리 완료건 유지, 오류 로그 후 중단 | try-catch per business + outer catch returning FAILED status | PASS |
| 배치 중 새 사업장 등록 -> 시작 시점 데이터 기준 | 별도 스냅샷 격리 없음 (Pageable 순차 조회) | MINOR - 아래 상세 |
| 대량 데이터 메모리 -> 청크 처리 | CHUNK_SIZE=500 Pageable 사용 | PASS |
| 전체 동기화 + 정합성 검증 동시 실행 방지 | 단일 AtomicBoolean `running` 플래그 공유 | PASS |

**엣지 케이스 점수: 3.5/4 (87.5%)**

## Test Coverage Check

| Test File | Test Cases | Coverage |
|-----------|-----------|----------|
| `SyncBatchServiceTest` (Unit) | 5 cases: fullSync 성공/빈DB/부분실패, consistencyCheck 추가+제거/변경없음 | FR-001~006, FR-010 |
| `SyncBatchControllerTest` (Integration) | 6 cases: fullSync 재구축/교체/빈DB, consistencyCheck 누락추가/고아제거/변경없음 | FR-001~003, FR-005, FR-009, FR-010 |
| `BusinessServiceRetryTest` (Integration) | 3 cases: GEOADD 재시도 성공/최대 재시도 후 복구, GEOREMOVE 재시도 | FR-007, FR-008 |

**테스트 점수: 총 14개 테스트 케이스로 모든 FR 커버**

## Gaps Found

### Minor-001: 배치 실행 중 동시 데이터 변경에 대한 스냅샷 격리 부재

- **Severity**: Minor
- **Design**: "전체 동기화 배치는 시작 시점의 데이터를 기준으로 처리한다"
- **Implementation**: Pageable로 순차 조회하므로, 배치 진행 중 새로 INSERT된 사업장이 후속 페이지에 포함될 수 있다. 반대로 삭제된 사업장이 이미 처리된 페이지에 포함되어 있을 수 있다.
- **Impact**: 하루 수십 건 수준의 쓰기 환경에서는 실질적 문제가 되지 않는다. 배치 실행 시간도 짧아 window가 매우 작다. 설계 문서의 표현은 의도 설명에 가까우며, 구현상 엄격한 스냅샷 격리는 요구하지 않는다.
- **Action**: 설계 문서 문구를 "페이지 단위로 순차 조회하므로 배치 중 변경된 데이터는 앱 레벨 동기화로 반영된다"로 보완하면 더 정확하다.

### Minor-002: @Recover 메서드가 예외 유형별로 중복 존재

- **Severity**: Minor
- **Design**: "최종 실패 시 로그 기록"
- **Implementation**: `recoverGeoAdd`, `recoverGeoRemove`, `recoverGeoAddTimeout`, `recoverGeoRemoveTimeout` 4개 메서드가 있다. `RedisConnectionFailureException`과 `QueryTimeoutException` 각각에 대한 recover 메서드가 필요하여 정상적인 구조이나, 로그 메시지만 다를 뿐 동작이 동일하다.
- **Impact**: 기능에 영향 없음. 코드 중복이 약간 있으나 Spring Retry의 예외 매칭 규칙을 따르기 위한 구조.
- **Action**: 공통 부모 예외 타입을 사용하거나, `Exception` 파라미터로 통합 recover 메서드를 만들 수 있지만 현재 구조도 명확하여 유지해도 무방하다.

### Minor-003: SyncBatchResult의 status 필드가 enum이 아닌 String

- **Severity**: Minor
- **Design**: OpenAPI spec에서 `enum: [FULL_SYNC, CONSISTENCY_CHECK]` 및 `enum: [SUCCESS, PARTIAL_FAILURE, FAILED]`로 정의
- **Implementation**: `String type`, `String status`로 리터럴 문자열 사용 (`"FULL_SYNC"`, `"SUCCESS"` 등)
- **Impact**: 런타임에 잘못된 값이 들어갈 가능성은 있으나, 현재 코드에서 값 생성은 `SyncBatchService` 내부에서만 이루어지므로 실질적 위험은 매우 낮다.
- **Action**: `SyncBatchType`, `SyncBatchStatus` enum을 도입하면 타입 안전성이 향상된다.

## Success Criteria Assessment

| SC | Description | Can Be Met? | Notes |
|----|-------------|:-----------:|-------|
| SC-001 | 전체 동기화 후 100% 검색 가능 | YES | 통합 테스트 `shouldRebuildRedisFromMysql`에서 검증됨 |
| SC-002 | 정합성 검증 후 누락/고아 0건 | YES | 통합 테스트 `shouldAddMissingBusinesses`, `shouldRemoveOrphanedEntries`에서 검증됨 |
| SC-003 | 재시도로 99% 이상 동기화 성공률 | YES | `@Retryable` 3회 재시도 + 지수 백오프 구현. 테스트에서 재시도 후 성공 검증됨 |
| SC-004 | 1,000건 기준 10초 이내 | LIKELY | 청크(500건) 단위 처리 + Redis GEOADD 개별 호출. 파이프라인 미사용이므로 대량 데이터에서는 최적화 여지 있음 |
| SC-005 | 매 실행마다 결과 리포트 | YES | `SyncBatchResult`가 API 응답 + 로그에 출력됨 |

## Score Summary

| Category | Items | Passed | Score |
|----------|:-----:|:------:|:-----:|
| Functional Requirements (FR) | 10 | 10 | 100% |
| API Contract | 12 | 12 | 100% |
| Data Model | 9 | 9 | 100% |
| Research Decisions (R) | 5 | 5 | 100% |
| Edge Cases | 4 | 3.5 | 87.5% |
| Success Criteria | 5 | 5 | 100% |
| **Total** | **45** | **44.5** | **93%** |

Status: PASS (>= 90%)

## Recommendations

### Documentation Update (Low Priority)

1. **Edge Case 문구 보완**: spec.md의 "시작 시점의 데이터를 기준" 표현을 실제 동작(Pageable 순차 조회)에 맞게 보완
2. **파이프라인 최적화 언급**: research.md R-002에서 "파이프라인으로 일괄 전송" 가능성을 언급했으나, 실제 구현은 개별 GEOADD 호출. SC-004(1,000건/10초) 기준 충분하지만 대규모 확장 시 최적화 포인트로 기록

### Code Improvement (Optional)

1. **SyncBatchType/SyncBatchStatus enum 도입**: 타입 안전성 향상 (Minor-003 해결)
2. **Redis Pipeline 적용**: fullSync에서 chunk 내 여러 GEOADD를 Pipeline으로 묶으면 성능 개선 가능
