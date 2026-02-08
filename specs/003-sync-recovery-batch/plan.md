# Implementation Plan: 앱 레벨 동기화 + 복구용 배치

**Branch**: `003-sync-recovery-batch` | **Date**: 2026-02-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-sync-recovery-batch/spec.md`

## Summary

Phase 2에서 구현한 앱 레벨 동기화(사업장 CRUD 시 Redis 즉시 반영)에 장애 대응 기능을 추가한다.
1) Redis 장애 시 MySQL 전체 데이터로 검색 인덱스를 재구축하는 전체 동기화 배치
2) MySQL-Redis 간 누락/고아 데이터를 찾아 보정하는 정합성 검증 배치
3) 앱 레벨 동기화 실패 시 자동 재시도(최대 3회)

ADR-4(데이터 동기화 전략)에서 정의한 "배치는 복구/보정 용도로만" 원칙을 구현하는 최종 Phase다.

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: Spring Boot 3.4.1, Spring Data JPA (Hibernate), Spring Data Redis (Lettuce), Spring Retry, Lombok
**Storage**: MySQL (원본 데이터) + Redis GEO (검색 인덱스) — Phase 1/2에서 구성 완료
**Testing**: JUnit 5 + Mockito (단위) + Testcontainers (통합)
**Target Platform**: Linux server (Docker Compose 개발 환경)
**Project Type**: single (Spring Boot 모놀리식, 논리적 Read/Write 분리)
**Performance Goals**: 배치 처리 1,000건 기준 10초 이내, 앱 레벨 동기화 재시도 포함 3초 이내 완료
**Constraints**: 기존 Phase 1/2 코드와 동일한 패키지 구조 유지, 새로운 인프라 추가 없음
**Scale/Scope**: 배치 서비스 1개, 컨트롤러 1개, 기존 BusinessService 수정, FR 10개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판정 | 근거 |
|------|------|------|
| I. ADR-Driven Design | PASS | PRD ADR-4(동기화 전략)의 "배치는 복구/보정 용도" 구현. 신규 ADR 불필요 |
| II. Simplicity First | PASS | Spring Retry로 재시도 처리(별도 큐 미사용). 배치는 수동 트리거만 지원(스케줄러 미도입) |
| III. Data Locality | PASS | Redis에는 여전히 business_id + 좌표만. 배치도 이 원칙을 따름 |
| IV. Read/Write Separation | PASS | 배치는 Business 서비스 영역. Search 서비스에 영향 없음 |
| V. Study-First Implementation | PASS | 데이터 정합성, 장애 복구 전략, 배치 처리 패턴 학습 포인트 포함 |

## Project Structure

### Documentation (this feature)

```text
specs/003-sync-recovery-batch/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── batch-api.yaml
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/proximityservice/
├── batch/
│   └── SyncBatchService.java         # (신규) 전체 동기화 + 정합성 검증 배치 로직
├── controller/
│   └── SyncBatchController.java      # (신규) 배치 수동 트리거 엔드포인트
├── dto/
│   └── SyncBatchResult.java          # (신규) 배치 실행 결과 DTO
├── service/
│   └── BusinessService.java          # (수정) Redis 동기화에 재시도 로직 추가
├── repository/
│   └── BusinessGeoRepository.java    # (수정) 전체 멤버 조회 메서드 추가
└── config/
    └── RetryConfig.java              # (신규) Spring Retry 설정

src/test/java/com/proximityservice/
├── batch/
│   └── SyncBatchServiceTest.java     # (신규) 배치 단위 테스트
├── controller/
│   └── SyncBatchControllerTest.java  # (신규) 배치 통합 테스트
└── service/
    └── BusinessServiceRetryTest.java # (신규) 재시도 로직 테스트
```

**Structure Decision**: Phase 1/2 구조를 유지하며, 배치 관련 코드는 `batch` 패키지에 분리. 기존 서비스/리포지토리는 최소한으로 수정.

## Complexity Tracking

Constitution Check에서 위반 사항 없음. 추가 복잡도 기록 불필요.
