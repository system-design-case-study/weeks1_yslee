<!--
Sync Impact Report
==================
Version change: N/A → 1.0.0 (initial creation)
Added sections:
  - Core Principles (5 principles)
  - Technical Constraints
  - Development Workflow
  - Governance
Templates status:
  - .specify/templates/plan-template.md ✅ compatible (Constitution Check section exists)
  - .specify/templates/spec-template.md ✅ compatible (no changes needed)
  - .specify/templates/tasks-template.md ✅ compatible (phase-based structure aligns)
Follow-up TODOs: None
-->

# Proximity Service Constitution

## Core Principles

### I. ADR-Driven Design

모든 아키텍처 결정은 Architecture Decision Record(ADR)로 문서화한다.
스터디 프로젝트의 핵심 가치는 "왜 이 선택을 했는가"를 설명할 수 있는 것이다.

- 기술 선택 시 후보군 비교와 트레이드오프를 MUST 기록한다.
- 결정의 흐름(어떤 판단이 다음 판단으로 이어졌는가)을 MUST 기록한다.
- 책의 내용과 다른 결정을 내렸을 경우, 차이점과 근거를 MUST 기록한다.
- ADR은 `docs/prd.md` 섹션 7에 관리한다.

### II. Simplicity First

요구사항을 만족하는 가장 단순한 해법을 선택한다.
복잡한 솔루션은 단순한 대안이 부족하다는 근거가 있을 때만 허용한다.

- 하루 수십 건의 쓰기에 CDC 파이프라인을 도입하지 않는다 (앱 레벨 동기화로 충분).
- Redis GEO가 처리하는 것을 별도 알고리즘으로 재구현하지 않는다.
- 스터디 범위를 벗어나는 기능(인증, 리뷰, 결제 등)은 추가하지 않는다.
- 복잡도를 추가할 때는 plan-template의 Complexity Tracking에 MUST 기록한다.

### III. Data Locality

각 저장소에는 해당 역할에 필요한 최소한의 데이터만 저장한다.

- Redis: business_id + 좌표만 저장한다. 상세 데이터를 MUST NOT 저장한다.
- MySQL: 원본 데이터의 단일 소스(Single Source of Truth)로 유지한다.
- Redis 갱신은 좌표 관련 변경(신규/삭제/이전)에만 발생한다.
  상세 정보 변경은 MySQL만 업데이트한다.

### IV. Read/Write Separation

검색(Search)과 관리(Business)는 독립적인 서비스로 분리한다.

- Search 서비스는 Redis를 통한 읽기 전용이다.
- Business 서비스는 MySQL 쓰기 + Redis 동기화를 담당한다.
- Business 서비스 장애 시에도 Search 서비스는 MUST 정상 동작한다.
- 각 서비스는 독립적으로 스케일링 가능해야 한다.

### V. Study-First Implementation

학습이 최우선 목적이다. 프로덕션 완성도보다 개념 이해와 설계 근거 기록을 우선한다.

- 각 Phase 완료 시 학습 포인트를 SHOULD 정리한다.
- 책의 설계와 실제 구현 사이의 차이가 발생하면 ADR로 기록한다.
- 용량 추정, 메모리 계산 등의 과정을 생략하지 않고 문서에 MUST 남긴다.
- "왜?"에 답할 수 없는 코드는 작성하지 않는다.

## Technical Constraints

### Technology Stack

| 계층 | 기술 | 역할 |
|------|------|------|
| 검색 인덱스 | Redis GEO | Geohash 기반 위치 검색 |
| 원본 저장소 | MySQL | 사업장 CRUD, PK 기반 조회 |
| API | RESTful | v1 버전 prefix 사용 |

### Performance Targets

| 항목 | 목표 |
|------|------|
| 검색 응답 지연 | 100ms 이내 |
| 평균 QPS | 5,800 |
| Peak QPS | 11,600 |
| Redis 메모리 | 약 20GB (POI 2억 기준) |

### Scope Boundaries

**포함:** 주변 검색 API, 사업장 CRUD, Redis 인덱스, 앱 레벨 동기화, 복구용 배치
**제외:** 사용자 인증, 리뷰/평점, 실시간 영업 표시, 즐겨찾기, 결제/예약

## Development Workflow

### Phase-Based Implementation

| 단계 | 기능 | 학습 포인트 |
|------|------|------------|
| Phase 1 | 주변 검색 API + Geohash 인덱싱 | Geohash 알고리즘, Redis GEO, 경계값 문제 |
| Phase 2 | 사업장 CRUD API | REST 설계, DB 스키마, Read/Write 분리 |
| Phase 3 | 앱 레벨 동기화 + 복구용 배치 | 데이터 정합성, 장애 복구 전략 |

### Document Flow

```
docs/prd.md (요구사항 + ADR)
  → /speckit.specify (기능별 명세)
    → /speckit.plan (기술 계획)
      → /speckit.tasks (구현 태스크)
```

- 기능 구현 전 명세(spec.md)를 MUST 먼저 작성한다.
- 명세는 PRD의 해당 Phase를 기반으로 한다.
- 구현 중 설계 변경이 필요하면 PRD의 ADR을 먼저 업데이트한다.

## Governance

- 이 Constitution은 프로젝트의 모든 설계/구현 판단에 우선한다.
- 원칙에 어긋나는 구현은 Complexity Tracking에 위반 사유를 기록해야 한다.
- 원칙의 수정은 ADR로 근거를 남긴 후 이 문서를 개정한다.
- 스터디 과정에서 원칙이 부적절하다고 판단되면 ADR과 함께 수정할 수 있다.

**Version**: 1.0.0 | **Ratified**: 2026-02-06 | **Last Amended**: 2026-02-06
