# Gap Analysis: 주변 검색 API (Nearby Search API)

**Date**: 2026-02-06 | **Match Rate**: 98.7% | **Status**: PASS

## Overall Scores

| Category | Items | Matches | Score | Status |
|----------|:-----:|:-------:|:-----:|:------:|
| Functional Requirements (FR-001~008) | 8 | 8 | 100% | PASS |
| User Story Acceptance Scenarios | 7 | 7 | 100% | PASS |
| Data Model Fields | 20 | 20 | 100% | PASS |
| API Contract Compliance | 13 | 12 | 92% | WARN |
| Project Structure | 22 | 22 | 100% | PASS |
| Edge Case Coverage | 4 | 4 | 100% | PASS |
| Success Criteria Achievability | 5 | 5 | 100% | PASS |
| **Overall** | **79** | **78** | **98.7%** | **PASS** |

## Gap Found (1건)

### POST /v1/businesses/seed 요청 검증 누락

- **Severity**: Medium
- **Design**: OpenAPI contract에 `required: [name, address, latitude, longitude, category]` + lat/lng min/max 지정
- **Implementation**: `BusinessSeedRequest`에 Bean Validation 없음. 잘못된 시딩 데이터 시 DB/Redis 에러 발생

**수정 필요 파일**:
1. `build.gradle.kts` — `spring-boot-starter-validation` 의존성 추가
2. `BusinessSeedRequest.java` — `@NotBlank`, `@NotNull`, `@Min`, `@Max` 어노테이션 추가
3. `BusinessSeedController.java` — `@Valid` 추가
4. `GlobalExceptionHandler.java` — `MethodArgumentNotValidException` 핸들러 추가

## Fully Matched Items

- FR-001~008: 전체 기능 요구사항 구현 완료
- US1~US3: 3개 User Story 7개 시나리오 전체 테스트 커버
- Data Model: Business 엔티티 10개 필드, Redis GEO 구조 완전 일치
- Project Structure: plan.md 대비 22개 파일 전체 일치
- Edge Cases: 경계값, (0,0) 좌표, 1m 반경, 동일 위치 전부 처리
- Architecture: Controller → Service → Repository 의존성 방향 정확
