# Implementation Plan: 주변 검색 API (Nearby Search API)

**Branch**: `001-nearby-search-api` | **Date**: 2026-02-06 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-nearby-search-api/spec.md`

## Summary

사용자 위치(위도, 경도)와 반경을 기반으로 주변 사업장을 검색하는 REST API를 구현한다.
Redis GEO 명령(GEOSEARCH)으로 반경 내 사업장 ID를 조회하고, MySQL PK 조회로 상세 정보를 가져와 거리순으로 반환한다.

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: Spring Boot 3.4.x, Spring Data Redis (Lettuce), Spring Data JPA
**Storage**: MySQL 8.0 (원본 데이터) + Redis 7 (검색 인덱스, GEO)
**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (MySQL, Redis)
**Target Platform**: Linux server / Docker
**Project Type**: Single project (API-only, 프론트엔드 없음)
**Performance Goals**: 검색 응답 100ms 이내, 5,800 QPS (평균)
**Constraints**: Redis 메모리 약 20GB (POI 2억 기준), 반경 최대 20km
**Scale/Scope**: 스터디 프로젝트. 로컬 Docker Compose 환경에서 동작 검증.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. ADR-Driven Design | PASS | 기술 선택 근거가 research.md에 기록됨 |
| II. Simplicity First | PASS | Spring Boot 기본 내장 Lettuce 사용, 별도 알고리즘 구현 없음 |
| III. Data Locality | PASS | Redis에 ID+좌표만 저장, 상세 데이터는 MySQL에서 PK 조회 |
| IV. Read/Write Separation | PASS | Phase 1은 Search 서비스(읽기)에 집중. 시딩용 쓰기 엔드포인트만 포함 |
| V. Study-First | PASS | research.md에 기술 선택 과정 기록, quickstart.md에 검증 절차 포함 |

## Project Structure

### Documentation (this feature)

```text
specs/001-nearby-search-api/
├── spec.md                          # Feature specification
├── plan.md                          # This file
├── research.md                      # Phase 0: Technology research
├── data-model.md                    # Phase 1: Entity & data structure
├── quickstart.md                    # Phase 1: Local run & test guide
├── contracts/
│   └── nearby-search-api.yaml       # Phase 1: OpenAPI contract
├── checklists/
│   └── requirements.md              # Spec quality checklist
└── tasks.md                         # Phase 2: Implementation tasks (/speckit.tasks)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/proximityservice/
│   │   ├── ProximityServiceApplication.java
│   │   ├── domain/
│   │   │   └── Business.java                 # JPA Entity
│   │   ├── dto/
│   │   │   ├── NearbySearchResponse.java     # 검색 응답 DTO
│   │   │   ├── BusinessSearchResult.java     # 개별 검색 결과
│   │   │   ├── BusinessSeedRequest.java      # 시딩 요청 DTO
│   │   │   └── ErrorResponse.java            # 에러 응답
│   │   ├── repository/
│   │   │   ├── BusinessRepository.java       # JPA Repository
│   │   │   └── BusinessGeoRepository.java    # Redis GEO 조작
│   │   ├── service/
│   │   │   ├── NearbySearchService.java      # 검색 비즈니스 로직
│   │   │   └── BusinessSeedService.java      # 시딩 로직
│   │   ├── controller/
│   │   │   ├── NearbySearchController.java   # GET /v1/search/nearby
│   │   │   └── BusinessSeedController.java   # POST /v1/businesses/seed
│   │   ├── config/
│   │   │   └── RedisConfig.java              # RedisTemplate 설정
│   │   └── exception/
│   │       ├── GlobalExceptionHandler.java   # @ControllerAdvice
│   │       └── InvalidParameterException.java
│   └── resources/
│       ├── application.yml
│       └── data.sql                          # 초기 시딩 데이터 (선택)
├── test/
│   └── java/com/proximityservice/
│       ├── controller/
│       │   └── NearbySearchControllerTest.java
│       ├── service/
│       │   └── NearbySearchServiceTest.java
│       └── repository/
│           └── BusinessGeoRepositoryTest.java

build.gradle.kts
docker-compose.yml
```

**Structure Decision**: Single project 구조 채택. Phase 1에서는 Search 기능만 구현하므로
프론트엔드/별도 서비스 분리 불필요. Phase 2에서 Business CRUD 추가 시에도 같은 프로젝트 내
패키지로 분리하되, 서비스 분리는 Phase 3 이후 고려.

## Complexity Tracking

> 위반 사항 없음. Constitution Check 전체 통과.
