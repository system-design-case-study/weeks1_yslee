# Research: 앱 레벨 동기화 + 복구용 배치

## R-001: 재시도 전략 — Spring Retry vs 직접 구현

**Decision**: Spring Retry 사용

**Rationale**:
- `@Retryable` 어노테이션으로 재시도 로직을 비즈니스 코드와 분리할 수 있다.
- maxAttempts, backoff(지수 백오프) 설정을 선언적으로 관리한다.
- `@Recover` 메서드로 최종 실패 시 폴백 처리(로그 기록)를 깔끔하게 구현한다.
- Spring Boot 생태계에 이미 포함되어 별도 인프라 추가 없이 의존성만 추가하면 된다.

**Alternatives considered**:
- **직접 for-loop 재시도**: 단순하지만 backoff, 예외 분류 등을 직접 관리해야 한다. 코드 중복 발생.
- **Resilience4j**: 서킷 브레이커, 레이트 리미터 등 풍부한 기능이 있으나, 하루 수십 건 수준의 쓰기에는 과한 도구다.
- **메시지 큐(RabbitMQ/Kafka)**: 비동기 재시도에 적합하나, 인프라 복잡도 급증. Simplicity First 원칙 위반.

## R-002: 배치 처리 패턴 — 청크(페이지) 기반 처리

**Decision**: Spring Data JPA의 Pageable을 사용한 청크 단위 처리

**Rationale**:
- `findAll(Pageable)` 으로 페이지 단위 조회하면 메모리에 전체 데이터를 올리지 않는다.
- 각 청크를 Redis에 파이프라인으로 일괄 전송하면 네트워크 라운드트립을 줄일 수 있다.
- Spring Batch를 도입하지 않고도 충분히 간단한 배치 처리가 가능하다.

**Alternatives considered**:
- **Spring Batch**: Job, Step, Reader/Writer 등 풍부한 추상화가 있으나, 단순 동기화에는 과한 프레임워크. 학습 비용 대비 실용성이 낮다.
- **JPA Stream (findAll → Stream)**: 메모리 효율적이지만 커서 기반이라 트랜잭션을 오래 유지해야 한다.
- **전체 한 번에 로드**: 데이터가 수백만 건이면 OOM 위험.

## R-003: 전체 동기화 전략 — 초기화 후 재구축 vs 증분 동기화

**Decision**: Redis GEO 키 삭제 후 전체 재구축 (DEL + GEOADD)

**Rationale**:
- 전체 동기화의 목적이 "장애 복구"이므로, 기존 데이터의 정확성을 신뢰할 수 없다. 깨끗하게 재구축하는 것이 안전하다.
- Redis GEO는 단일 키(`geo:businesses`)에 모든 데이터가 있으므로, DEL 한 번으로 초기화가 가능하다.
- GEOADD는 파이프라인으로 묶으면 수천 건을 밀리초 단위로 처리할 수 있다.

**Alternatives considered**:
- **증분 동기화(diff 계산 후 반영)**: 변경분만 처리하므로 효율적이지만, 정합성 보장이 어렵다. 전체 동기화의 목적에 맞지 않음.
- **새 키에 구축 후 교체(Blue-Green)**: `geo:businesses:new`에 구축 후 RENAME. 무중단 전환이 가능하지만, 검색 서비스가 키 이름을 하드코딩하고 있어 변경 비용이 있다. 향후 개선 가능.

## R-004: 정합성 검증 전략 — 양방향 비교

**Decision**: MySQL 전체 ID 집합과 Redis 전체 멤버 집합을 비교하여 차이를 보정

**Rationale**:
- MySQL의 모든 business_id를 조회하고, Redis GEO의 모든 멤버를 조회한 뒤 집합 연산으로 비교한다.
- `MySQL - Redis = 누락(추가 필요)`, `Redis - MySQL = 고아(제거 필요)`
- 좌표 비교는 하지 않는다. 좌표가 바뀌는 경우는 앱 레벨 동기화에서 이미 처리하며, 매우 드문 이벤트다.

**Alternatives considered**:
- **좌표까지 비교**: Redis GEOPOS로 좌표를 가져와 MySQL과 비교. 정확도는 높지만, N건 × GEOPOS 호출 비용이 크고, Geohash 정밀도 차이로 미세한 오차가 있을 수 있다.
- **체크섬 비교**: 해시값으로 빠르게 비교. 구현 복잡도 대비 이점이 적다.

## R-005: 배치 중복 실행 방지 — AtomicBoolean vs 분산 락

**Decision**: JVM 내 AtomicBoolean 플래그로 중복 실행 방지

**Rationale**:
- 현재 단일 인스턴스 배포이므로 JVM 레벨 동시성 제어로 충분하다.
- AtomicBoolean으로 배치 실행 중 여부를 체크하고, 중복 요청 시 즉시 거절한다.
- 배치 완료 또는 예외 발생 시 finally 블록에서 플래그를 해제한다.

**Alternatives considered**:
- **Redis 분산 락(SETNX)**: 다중 인스턴스 환경에서 필요하지만, 현재는 단일 인스턴스. 오버엔지니어링.
- **DB 기반 락**: 배치 이력 테이블에 상태 플래그. 불필요한 테이블 추가.
- **없음(동시 실행 허용)**: 전체 동기화 중 정합성 배치가 돌면 예측 불가능한 결과.
