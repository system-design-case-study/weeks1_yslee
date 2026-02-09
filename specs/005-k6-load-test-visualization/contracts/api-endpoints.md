# API Contracts: k6 부하 테스트 대상 엔드포인트

## 기존 API (변경 없음)

Phase 1~3에서 정의한 API 그대로 부하 테스트 대상으로 사용한다.

### 주변 검색

```
GET /v1/search/nearby?latitude={lat}&longitude={lng}&radius={radius}
```

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| latitude | decimal | Y | - | 위도 (-90 ~ 90) |
| longitude | decimal | Y | - | 경도 (-180 ~ 180) |
| radius | integer | N | 5000 | 검색 반경 (미터) |

**Response** `200 OK`:
```json
[
  {
    "id": "uuid",
    "name": "string",
    "address": "string",
    "latitude": 37.4979,
    "longitude": 127.0276,
    "category": "RESTAURANT",
    "distance": 123.4
  }
]
```

### 사업장 CRUD

```
POST   /v1/businesses          # 생성
GET    /v1/businesses/{id}     # 상세 조회
PUT    /v1/businesses/{id}     # 수정
DELETE /v1/businesses/{id}     # 삭제
```

### 시딩

```
POST /v1/businesses/seed       # 대량 시딩 (k6 setup 단계에서 사용)
```

**Request Body**:
```json
{
  "count": 1000,
  "centerLatitude": 37.5512,
  "centerLongitude": 126.9882,
  "radiusMeters": 15000
}
```

## 신규 엔드포인트

### Prometheus 메트릭 (Actuator)

```
GET /actuator/prometheus
```

Spring Boot Actuator가 자동 노출. Prometheus text exposition format으로 메트릭 반환.

**Response** `200 OK` (text/plain):
```
# HELP http_server_requests_seconds_count
# TYPE http_server_requests_seconds_count counter
http_server_requests_seconds_count{method="GET",uri="/v1/search/nearby",status="200"} 1234
...
```
