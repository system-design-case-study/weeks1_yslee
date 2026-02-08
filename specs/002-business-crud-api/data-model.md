# Data Model: 사업장 CRUD API

**Date**: 2026-02-08

## Entities

### Business (기존 — 변경사항 포함)

Phase 1에서 정의한 엔티티를 그대로 사용하되, 필드 업데이트를 위한 `update()` 메서드를 추가한다.

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | String (UUID) | PK, 36자 | 자동 생성 |
| name | String | NOT NULL, 255자 | 사업장 이름 |
| address | String | NOT NULL, 500자 | 주소 |
| latitude | Double | NOT NULL, DECIMAL(10,7) | 위도 (-90 ~ 90) |
| longitude | Double | NOT NULL, DECIMAL(10,7) | 경도 (-180 ~ 180) |
| category | String | NOT NULL, 50자 | Category enum 값 |
| phone | String | nullable, 20자 | 전화번호 |
| hours | String | nullable, 100자 | 영업시간 |
| created_at | LocalDateTime | NOT NULL, 자동 | 등록 일시 |
| updated_at | LocalDateTime | NOT NULL, 자동 | 수정 일시 |

**추가 메서드**: `update(name, address, latitude, longitude, category, phone, hours)` — 모든 필드를 갱신하고, 좌표 변경 여부를 반환한다.

### Category (신규 — enum)

사업장 분류 체계. DB 저장 시에는 소문자 문자열로 변환.

| 값 | 설명 |
|----|------|
| KOREAN_FOOD | 한식 |
| CHINESE_FOOD | 중식 |
| JAPANESE_FOOD | 일식 |
| WESTERN_FOOD | 양식 |
| CAFE | 카페 |
| BAR | 술집 |
| CONVENIENCE | 편의점 |
| PHARMACY | 약국 |
| HAIR_SALON | 미용실 |
| GYM | 헬스장 |

## Validation Rules

| 필드 | 규칙 |
|------|------|
| name | 필수, 1~255자, 공백만으로 구성 불가 |
| address | 선택 (등록 시), 최대 500자 |
| latitude | 필수, -90.0 ~ 90.0 |
| longitude | 필수, -180.0 ~ 180.0 |
| category | 필수, Category enum에 정의된 값만 허용 |
| phone | 선택, 최대 20자 |
| hours | 선택, 최대 100자 |

## State Transitions

```
(없음) --[POST /v1/businesses]--> 생성됨
생성됨 --[PUT /v1/businesses/:id]--> 수정됨
생성됨/수정됨 --[DELETE /v1/businesses/:id]--> 삭제됨 (Hard Delete)
```

## Redis 동기화 규칙

| CRUD 작업 | MySQL | Redis |
|-----------|-------|-------|
| CREATE | INSERT | GEOADD |
| READ | SELECT (PK) | - |
| UPDATE (좌표 변경 없음) | UPDATE | - |
| UPDATE (좌표 변경 있음) | UPDATE | ZREM + GEOADD |
| DELETE | DELETE | ZREM |
