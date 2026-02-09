# Research: 책 조건 검증 + 성능 + E2E 테스트

## R-001: Testcontainers 재사용 전략 — 싱글톤 vs @Container

**Decision**: 싱글톤 컨테이너 (static initializer + `@DynamicPropertySource`)

**Rationale**:
- `@Container` + `@Testcontainers`를 abstract 부모 클래스에 사용하면, JUnit이 각 자식 테스트 클래스를 독립 생명주기로 관리하여 첫 번째 자식 클래스 완료 시 컨테이너가 중지된다.
- 후속 자식 클래스는 이미 중지된 컨테이너의 포트를 참조하여 `ConnectException` 발생.
- 싱글톤 패턴은 static initializer에서 컨테이너를 시작하고, JVM 종료 시까지 유지한다.
- 기존 테스트 클래스(controller/, service/, batch/)는 자체 `@Container`를 사용하므로 충돌 없이 공존.

**Alternatives considered**:
- **`@Container` + `@Testcontainers` 직접 사용**: 각 자식 클래스에 컨테이너 정의를 복붙해야 함. 코드 중복.
- **Testcontainers JDBC URL 방식**: `jdbc:tc:mysql:8.0:///proximity` URL로 자동 컨테이너 생성. Redis 지원 불가.
- **`@SharedContainerConfiguration`**: Spring Boot 3.x 미지원.

## R-002: Geohash 정밀도와 거리 검증

**Decision**: 동일 좌표 거리 검증 시 `distance < 1.0m` 허용

**Rationale**:
- Redis는 좌표를 52비트 geohash 정수(Sorted Set score)로 저장한다.
- 이 양자화 과정에서 소수점 이하에서 미세한 좌표 오차가 발생한다.
- `NearbySearchService`가 `Math.round(distanceM * 10.0) / 10.0`으로 1자리 반올림하므로, 원래 ~0.05m 이하면 0.0이 되지만, geohash 인코딩에 따라 0.1~0.3m이 나올 수 있다.
- Haversine 공식과 Redis GEODIST 사이의 1% 이내 오차는 별도 테스트로 검증.

## R-003: Gradle 테스트 태그 분리 방식

**Decision**: `tasks.named<Test>("test")`로 빌트인 test task만 대상

**Rationale**:
- `tasks.withType<Test>`는 모든 `Test` 타입 태스크에 적용되므로, 커스텀 `performanceTest` 태스크에도 `excludeTags("performance")`가 적용된다.
- 결과적으로 `performanceTest`가 `includeTags("performance")` + `excludeTags("performance")`를 모두 갖게 되어, Gradle이 exclude를 우선하여 테스트가 실행되지 않는다.
- `tasks.named<Test>("test")`로 범위를 한정하면 `performanceTest`에 영향 없음.

## R-004: 성능 테스트 측정 방법론

**Decision**: JUnit 내부 `System.nanoTime()` 기반 워밍업 + 반복 측정

**Rationale**:
- JVM 워밍업(JIT 컴파일, 커넥션 풀 초기화)을 고려하여 처음 10회는 측정에서 제외한다.
- 100회 반복 측정 후 정렬하여 p50/p95/p99를 인덱스 기반으로 산출한다.
- 외부 도구(k6, JMH) 없이도 기본적인 성능 기준 충족 여부를 확인할 수 있다.
- 보다 정밀한 부하 테스트(k6 + Prometheus + Grafana)는 향후 별도 Phase에서 구성 예정.

## R-005: 서울 랜드마크 좌표 선정

**Decision**: 강남/홍대/잠실/서울역/명동 5개 랜드마크

**Rationale**:
- 서울 내에서 충분히 분산된 위치로, 반경별 검색 테스트에 적합하다.
- 각 랜드마크 간 거리가 2~15km 범위로, 반경 500m/1km/3km/20km 테스트에 다양한 결과를 제공한다.
- 거리 매트릭스 출력으로 좌표 정확성을 시각적으로 검증할 수 있다.
