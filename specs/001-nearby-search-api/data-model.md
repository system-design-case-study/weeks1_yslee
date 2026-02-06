# Data Model: 주변 검색 API

**Branch**: `001-nearby-search-api` | **Date**: 2026-02-06

## Entities

### Business (사업장)

원본 데이터. MySQL에 저장.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID (string) | PK, NOT NULL | 고유 식별자 |
| name | VARCHAR(255) | NOT NULL | 사업장 이름 |
| address | VARCHAR(500) | NOT NULL | 주소 |
| latitude | DECIMAL(10,7) | NOT NULL, -90~90 | 위도 |
| longitude | DECIMAL(10,7) | NOT NULL, -180~180 | 경도 |
| category | VARCHAR(50) | NOT NULL | 카테고리 (korean_food, cafe 등) |
| phone | VARCHAR(20) | NULLABLE | 전화번호 |
| hours | VARCHAR(100) | NULLABLE | 영업시간 ("09:00-22:00") |
| created_at | DATETIME | NOT NULL, DEFAULT NOW | 등록일시 |
| updated_at | DATETIME | NOT NULL, ON UPDATE NOW | 수정일시 |

**Indexes:**
- PK: `id`
- Phase 1에서는 공간 인덱스 불필요 (Redis GEO가 처리)

### Search Result (검색 결과)

API 응답용 DTO. DB에 저장하지 않음.

| Field | Type | Description |
|-------|------|-------------|
| id | string | Business ID |
| name | string | 사업장 이름 |
| address | string | 주소 |
| latitude | decimal | 위도 |
| longitude | decimal | 경도 |
| distance_m | double | 요청 좌표로부터의 거리 (m) |
| category | string | 카테고리 |

## Redis Data Structure

| Key | Type | Member | Score |
|-----|------|--------|-------|
| `geo:businesses` | GEO (Sorted Set) | business_id (UUID string) | Geohash encoded value |

- `GEOADD geo:businesses <lng> <lat> <business_id>` 로 등록
- `GEOSEARCH geo:businesses FROMLONLAT <lng> <lat> BYRADIUS <radius> m ASC` 로 검색
- Redis에는 ID + 좌표만 저장. 상세 정보는 MySQL PK 조회로 가져온다.

## Data Flow

```
[검색 요청]
  → Redis GEOSEARCH → business_id 목록 + 거리
  → MySQL WHERE id IN (...) → 상세 정보
  → 합쳐서 SearchResult DTO 반환

[데이터 시딩]
  → MySQL INSERT (Business)
  → Redis GEOADD (좌표 + ID)
```

## Validation Rules

| Field | Rule |
|-------|------|
| latitude | -90.0 ~ 90.0 |
| longitude | -180.0 ~ 180.0 |
| radius | 1 ~ 20000 (m), 기본값 5000 |
| name | 비어 있으면 안 됨 |
| category | 허용된 값 목록 중 하나 |
