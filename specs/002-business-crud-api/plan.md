# Implementation Plan: 사업장 CRUD API

**Branch**: `002-business-crud-api` | **Date**: 2026-02-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-business-crud-api/spec.md`

## Summary

Phase 1에서 구현한 주변 검색 API에 이어, 사업장의 생성/조회/수정/삭제(CRUD) API를 구현한다.
핵심은 ADR-4(앱 레벨 동기화)에 따라 등록/삭제 시 MySQL과 Redis를 즉시 동기화하고,
수정 시에는 좌표 변경이 있을 때만 Redis를 갱신하는 것이다.

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: Spring Boot 3.4.1, Spring Data JPA (Hibernate), Spring Data Redis (Lettuce), Lombok, Jakarta Validation
**Storage**: MySQL (원본 데이터) + Redis GEO (검색 인덱스) — Phase 1에서 구성 완료
**Testing**: JUnit 5 + Testcontainers (MySQL)
**Target Platform**: Linux server (Docker Compose 개발 환경)
**Project Type**: single (Spring Boot 모놀리식, 논리적 Read/Write 분리)
**Performance Goals**: CRUD 쓰기는 하루 수십~수백 건 수준, 상세 조회는 PK 기반이므로 성능 병목 없음
**Constraints**: 기존 Phase 1 코드와 동일한 패키지 구조, 엔티티, Repository 재활용
**Scale/Scope**: Business 엔티티 기준 10개 FR, REST 엔드포인트 4개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. ADR-Driven Design | PASS | PRD ADR-4(동기화 전략)에 따라 설계. 새로운 ADR 필요 없음 |
| II. Simplicity First | PASS | 기존 엔티티/Repository 재활용, 새 인프라 추가 없음 |
| III. Data Locality | PASS | Redis에는 좌표+ID만. 상세 정보는 MySQL에서 조회 |
| IV. Read/Write Separation | PASS | CRUD는 Business 서비스 담당, Search 서비스와 분리 유지 |
| V. Study-First Implementation | PASS | REST 설계 패턴과 앱 레벨 동기화 학습 포인트 포함 |

## Project Structure

### Documentation (this feature)

```text
specs/002-business-crud-api/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── business-api.yaml
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/proximityservice/
├── domain/
│   ├── Business.java              # (기존) 수정 필드 업데이트 메서드 추가
│   └── Category.java              # (신규) 카테고리 enum
├── controller/
│   ├── BusinessController.java    # (신규) CRUD 엔드포인트
│   ├── BusinessSeedController.java # (기존)
│   └── NearbySearchController.java # (기존)
├── dto/
│   ├── BusinessCreateRequest.java  # (신규) 등록 요청
│   ├── BusinessUpdateRequest.java  # (신규) 수정 요청
│   ├── BusinessDetailResponse.java # (신규) 상세 조회 응답
│   └── ...                         # (기존 DTO들)
├── service/
│   ├── BusinessService.java        # (신규) CRUD 비즈니스 로직 + Redis 동기화
│   └── ...                         # (기존 서비스들)
├── exception/
│   ├── BusinessNotFoundException.java # (신규) 404 처리
│   └── GlobalExceptionHandler.java    # (기존) 핸들러 추가
└── repository/
    ├── BusinessRepository.java     # (기존)
    └── BusinessGeoRepository.java  # (기존)
```

**Structure Decision**: Phase 1 구조를 그대로 유지. 신규 파일은 기존 패키지에 추가하며, Business 엔티티에 update 메서드만 추가한다.

## Complexity Tracking

Constitution Check에서 위반 사항 없음. 추가 복잡도 기록 불필요.
