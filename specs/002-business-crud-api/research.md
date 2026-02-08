# Research: 사업장 CRUD API

**Date**: 2026-02-08
**Status**: Complete (NEEDS CLARIFICATION 없음)

## R-001: Business 엔티티 수정 전략

**Decision**: Business 엔티티에 `update()` 메서드를 추가하여 필드 단위 갱신 지원

**Rationale**:
- 기존 Business 엔티티는 생성자로만 필드를 설정하고 setter가 없음 (불변 설계)
- JPA의 dirty checking을 활용하려면 엔티티 내부에서 필드를 변경하는 메서드가 필요
- setter를 열지 않고 도메인 메서드(`update`)로 변경 범위를 제어하는 것이 DDD 관례

**Alternatives considered**:
- 전체 교체 방식 (delete + insert): 단순하나 Redis 동기화 판단(좌표 변경 여부)이 어려워짐
- Setter 개방: 캡슐화 위반, 어디서든 필드를 바꿀 수 있어 추적 어려움

## R-002: 좌표 변경 감지 방식

**Decision**: update 메서드 호출 전에 기존 좌표를 저장해두고, 변경 후 비교하여 Redis 갱신 여부 결정

**Rationale**:
- Business.update() 호출 전 latitude/longitude를 캡처
- 호출 후 캡처 값과 비교하여 변경 시에만 `geoRepository.remove() + add()` 수행
- 서비스 레이어에서 처리하며, 엔티티에 변경 감지 로직을 넣지 않음

**Alternatives considered**:
- Hibernate @PreUpdate 리스너: 엔티티에 Repository 의존성이 생겨 부적절
- JPA 변경 이벤트: 스터디 범위에서 오버엔지니어링

## R-003: 카테고리 검증 전략

**Decision**: Java enum (`Category`)으로 관리하고, 요청 시 문자열 → enum 변환으로 검증

**Rationale**:
- PRD에 카테고리가 사전 정의된 목록으로 명시됨
- DB에는 `String`으로 저장 (기존 스키마 유지), 입력 검증만 enum으로 수행
- enum을 사용하면 컴파일 타임에 유효값 목록이 확정되고, 오류 메시지에 허용 목록을 자동 포함 가능

**Alternatives considered**:
- DB 테이블로 관리: 동적 추가가 가능하지만 하드코딩된 목록이 PRD 요구사항이므로 불필요
- 문자열 상수 목록: 타입 안전성이 enum보다 떨어짐

## R-004: 삭제 전략 (Hard Delete vs Soft Delete)

**Decision**: Hard Delete 적용

**Rationale**:
- PRD에 삭제 복원 요구사항 없음
- 스터디 프로젝트로 Simplicity First 원칙 적용
- 삭제 시 MySQL DELETE + Redis ZREM으로 완전 제거

**Alternatives considered**:
- Soft Delete (`deleted_at` 컬럼): 복원 가능하지만 조회 시 항상 필터 조건 추가 필요, 현재 불필요한 복잡도

## R-005: 수정 API 방식 (PUT vs PATCH)

**Decision**: PUT (전체 교체) 방식 적용

**Rationale**:
- 엔티티 필드가 8개로 적어 부분 업데이트의 이점이 크지 않음
- PUT이 구현과 테스트가 단순하며, 프론트엔드에서도 전체 폼을 보내는 것이 일반적
- Simplicity First 원칙 준수

**Alternatives considered**:
- PATCH (부분 업데이트): 필드가 수십 개인 경우 유리하나 현재 스키마에서는 과도함
