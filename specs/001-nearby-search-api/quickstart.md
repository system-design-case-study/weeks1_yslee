# Quickstart: 주변 검색 API

## Prerequisites

- Java 21
- Docker & Docker Compose
- Gradle 8.x (wrapper 포함)

## 1. 로컬 환경 실행

```bash
# 프로젝트 루트에서
docker compose up -d        # MySQL + Redis 컨테이너 시작
./gradlew bootRun           # Spring Boot 앱 실행
```

> Spring Boot 3.1+의 `spring-boot-docker-compose` 의존성이 있으면
> `./gradlew bootRun`만으로 컨테이너가 자동 시작됩니다.

## 2. 데이터 시딩

```bash
curl -X POST http://localhost:8080/v1/businesses/seed \
  -H "Content-Type: application/json" \
  -d '[
    {
      "name": "맛있는 식당",
      "address": "서울시 강남구 테헤란로 123",
      "latitude": 37.5012,
      "longitude": 127.0396,
      "category": "korean_food",
      "phone": "02-1234-5678",
      "hours": "11:00-22:00"
    },
    {
      "name": "좋은 카페",
      "address": "서울시 강남구 역삼로 45",
      "latitude": 37.5025,
      "longitude": 127.0380,
      "category": "cafe",
      "phone": "02-9876-5432",
      "hours": "08:00-23:00"
    }
  ]'
```

## 3. 주변 검색

```bash
# 반경 1km 이내 검색
curl "http://localhost:8080/v1/search/nearby?latitude=37.5012&longitude=127.0396&radius=1000"

# 기본 반경 (5km) 검색
curl "http://localhost:8080/v1/search/nearby?latitude=37.5012&longitude=127.0396"
```

**예상 응답:**

```json
{
  "total": 2,
  "businesses": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "맛있는 식당",
      "address": "서울시 강남구 테헤란로 123",
      "latitude": 37.5012,
      "longitude": 127.0396,
      "distance_m": 0.0,
      "category": "korean_food"
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "name": "좋은 카페",
      "address": "서울시 강남구 역삼로 45",
      "latitude": 37.5025,
      "longitude": 127.0380,
      "distance_m": 210.5,
      "category": "cafe"
    }
  ]
}
```

## 4. 에러 케이스 확인

```bash
# 잘못된 위도
curl "http://localhost:8080/v1/search/nearby?latitude=91&longitude=127.0396"

# 반경 초과
curl "http://localhost:8080/v1/search/nearby?latitude=37.5&longitude=127.0&radius=50000"
```

## 5. 테스트 실행

```bash
./gradlew test              # 전체 테스트
./gradlew test --tests "*NearbySearchTest"  # 검색 테스트만
```

> Testcontainers가 테스트 시 MySQL/Redis 컨테이너를 자동으로 띄웁니다.
