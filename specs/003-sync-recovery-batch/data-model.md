# Data Model: 앱 레벨 동기화 + 복구용 배치

## 기존 엔티티 (수정 없음)

### Business (사업장)

Phase 1/2에서 정의 완료. 이번 Phase에서 엔티티 변경 없음.

| 필드 | 타입 | 설명 |
|------|------|------|
| id | string (UUID) | 고유 식별자 |
| name | string(255) | 사업장 이름 |
| address | string(500) | 주소 |
| latitude | decimal | 위도 (-90 ~ 90) |
| longitude | decimal | 경도 (-180 ~ 180) |
| category | string | 카테고리 |
| phone | string(20) | 전화번호 |
| hours | string(100) | 영업시간 |
| created_at | datetime | 등록일시 |
| updated_at | datetime | 수정일시 |

### Redis GEO (검색 인덱스)

| 키 | 자료구조 | 저장 데이터 |
|----|---------|------------|
| `geo:businesses` | GEO (Sorted Set) | member=business_id, 위경도 좌표 |

## 신규 모델

### SyncBatchResult (배치 실행 결과)

배치 실행의 결과를 담는 값 객체. DB에 저장하지 않고 API 응답 및 로그에 사용한다.

| 필드 | 타입 | 설명 |
|------|------|------|
| type | string | 배치 유형: `FULL_SYNC` 또는 `CONSISTENCY_CHECK` |
| status | string | 실행 결과: `SUCCESS`, `PARTIAL_FAILURE`, `FAILED` |
| total_processed | int | 처리된 총 건수 |
| added | int | Redis에 추가된 건수 |
| removed | int | Redis에서 제거된 건수 |
| errors | int | 오류 발생 건수 |
| started_at | datetime | 배치 시작 시각 |
| finished_at | datetime | 배치 종료 시각 |
| duration_ms | long | 소요 시간(ms) |

## 정합성 검증 로직

### 집합 비교

```
MySQL 전체 ID 집합: {A, B, C, D, E}
Redis 전체 멤버 집합: {A, B, C, F}

누락 (MySQL - Redis): {D, E}  → Redis에 GEOADD
고아 (Redis - MySQL): {F}      → Redis에서 ZREM
```

### 동기화 규칙

| 이벤트 | 배치 유형 | Redis 작업 |
|--------|----------|-----------|
| 전체 동기화 | FULL_SYNC | DEL geo:businesses → MySQL 전체 → GEOADD (청크 단위) |
| 누락 보정 | CONSISTENCY_CHECK | 누락 건 GEOADD |
| 고아 제거 | CONSISTENCY_CHECK | 고아 건 ZREM |

### 재시도 규칙 (앱 레벨 동기화)

| 설정 | 값 | 근거 |
|------|-----|------|
| 최대 시도 횟수 | 3회 | 일시적 네트워크 오류 대응에 충분 |
| 백오프 | 지수 백오프(1초 → 2초 → 4초) | 부하 회복 시간 확보 |
| 재시도 대상 예외 | Redis 연결/타임아웃 예외 | 영구적 오류(잘못된 키 등)는 재시도하지 않음 |
| 최종 실패 시 | 로그 기록 + MySQL 데이터 유지 | 정합성 배치에서 보정 |
