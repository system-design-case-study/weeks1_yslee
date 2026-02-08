# Quickstart: 사업장 CRUD API

## 전제 조건

- Phase 1 (주변 검색 API) 구현 완료 상태
- Docker Compose로 MySQL + Redis 실행 가능

## 개발 환경 실행

```bash
./gradlew bootRun
```

Spring Boot Docker Compose support가 MySQL, Redis 컨테이너를 자동 시작한다.

## API 테스트

### 1. 사업장 등록

```bash
curl -X POST http://localhost:8080/v1/businesses \
  -H "Content-Type: application/json" \
  -d '{
    "name": "맛있는 식당",
    "address": "서울시 강남구 테헤란로 123",
    "latitude": 37.5012,
    "longitude": 127.0396,
    "category": "korean_food",
    "phone": "02-1234-5678",
    "hours": "09:00-22:00"
  }'
```

### 2. 사업장 상세 조회

```bash
curl http://localhost:8080/v1/businesses/{id}
```

### 3. 사업장 수정

```bash
curl -X PUT http://localhost:8080/v1/businesses/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "name": "더 맛있는 식당",
    "address": "서울시 강남구 테헤란로 456",
    "latitude": 37.5012,
    "longitude": 127.0396,
    "category": "korean_food",
    "phone": "02-9876-5432",
    "hours": "10:00-23:00"
  }'
```

### 4. 사업장 삭제

```bash
curl -X DELETE http://localhost:8080/v1/businesses/{id}
```

### 5. 동기화 확인 (등록 후 검색)

```bash
curl "http://localhost:8080/v1/search/nearby?latitude=37.5012&longitude=127.0396&radius=1000"
```

## 검증 포인트

- [ ] 등록 후 즉시 주변 검색에 노출되는가
- [ ] 삭제 후 즉시 주변 검색에서 제외되는가
- [ ] 좌표 변경 수정 후 새 위치에서 검색되는가
- [ ] 좌표 미변경 수정 시 검색 인덱스가 갱신되지 않는가
- [ ] 잘못된 카테고리/좌표로 등록 시 400 오류가 반환되는가
