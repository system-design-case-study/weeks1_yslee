# Research: 주변 검색 API (Nearby Search API)

**Branch**: `001-nearby-search-api` | **Date**: 2026-02-06

## 1. Java Version

**Decision:** Java 21 (LTS)
**Rationale:** Spring Boot 3.x의 최소 요구사항은 Java 17이지만, Java 21은 최신 LTS로 virtual threads, records 등 현대적 기능을 제공하며 2029년까지 지원된다.
**Alternatives:** Java 17 (LTS, 가능하지만 구형), Java 25 (너무 최신)

## 2. Spring Boot Version

**Decision:** Spring Boot 3.4.x
**Rationale:** 안정적인 최신 3.x 라인. Spring Data Redis, Spring Data JPA, Docker Compose 통합을 모두 지원한다. 4.0은 너무 최신이라 커뮤니티 지원이 제한적.
**Key Dependencies:**
- `spring-boot-starter-data-redis` (Lettuce 내장)
- `spring-boot-starter-data-jpa` + MySQL Connector
- `spring-boot-starter-web`
- `spring-boot-docker-compose`

## 3. Redis Client

**Decision:** Lettuce (Spring Boot 기본 내장)
**Rationale:** 스레드 안전, 비동기 지원, 단일 커넥션 공유 가능. Spring Boot의 기본 Redis 클라이언트이므로 별도 설정 불필요.
**Alternatives:** Jedis (동기 전용, 커넥션 풀 필요, 멀티스레드 환경에서 불리)

## 4. Redis GEO API

**Decision:** Spring Data Redis `GeoOperations` 사용
**Rationale:** `GEOADD`, `GEOSEARCH` 명령을 Java 타입 안전 API로 제공. 거리 계산, 좌표 포함, 정렬, 제한 등을 메서드 체이닝으로 처리 가능.

```java
GeoOperations<String, String> geoOps = redisTemplate.opsForGeo();

// 등록
geoOps.add("geo:businesses", new Point(lng, lat), businessId);

// 검색
GeoResults<GeoLocation<String>> results = geoOps.search(
    "geo:businesses",
    GeoReference.fromCoordinate(lng, lat),
    new Distance(radius, Metrics.METERS),
    GeoSearchCommandArgs.newGeoSearchArgs()
        .includeDistance()
        .includeCoordinates()
        .sortAscending()
);
```

## 5. Testing

**Decision:** JUnit 5 + Spring Boot Test + Testcontainers
**Rationale:** Testcontainers로 실제 MySQL/Redis 인스턴스를 띄워 통합 테스트. H2나 Mock은 Redis GEO 같은 특수 기능을 검증할 수 없다. Spring Boot 3.1+의 `@ServiceConnection`으로 설정 자동화.
**Alternatives:** H2/Embedded Redis (실제 동작과 다를 수 있음)

## 6. Build Tool

**Decision:** Gradle Kotlin DSL
**Rationale:** Spring Initializr 기본값. 타입 안전한 빌드 스크립트, 빠른 빌드 성능(증분 컴파일, 빌드 캐시).
**Alternatives:** Maven (더 단순하지만 빌드 속도 느림)

## 7. Local Development

**Decision:** Docker Compose (MySQL 8.0 + Redis 7)
**Rationale:** Spring Boot 3.1+의 `spring-boot-docker-compose` 의존성으로 앱 실행 시 자동으로 컨테이너 시작/종료. 별도 설정 없이 서비스 연결 자동 구성.

## Summary Stack

```
Java 21 (LTS) + Spring Boot 3.4.x
+ Spring Data Redis (Lettuce, GeoOperations)
+ Spring Data JPA + MySQL 8.0
+ JUnit 5 + Testcontainers
+ Gradle Kotlin DSL
+ Docker Compose (MySQL + Redis)
```
