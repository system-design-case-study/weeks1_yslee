# Quickstart: 책 조건 검증 + 성능 + E2E 테스트

## 사전 조건

- Phase 1 (주변 검색 API) 완료
- Phase 2 (사업장 CRUD API) 완료
- Phase 3 (앱 레벨 동기화 + 복구 배치) 완료
- Docker 실행 중 (Testcontainers가 자동으로 MySQL + Redis 컨테이너 시작)

## 실행 방법

### 일반 테스트 (verification + scenario, 성능 제외)

```bash
./gradlew test
# 87 tests: 기존 43개 + 신규 verification 30개 + scenario 4개 + 기존 service/batch 10개
```

### 성능 테스트만

```bash
./gradlew performanceTest
# 11 tests: SearchLatency 3 + BatchPerformance 3 + Concurrent 2 + LargeDataset 3
```

### 특정 카테고리만

```bash
# 책 조건 검증만
./gradlew test --tests "com.proximityservice.verification.*"

# E2E 시나리오만
./gradlew test --tests "com.proximityservice.scenario.*"

# 특정 테스트 클래스만
./gradlew test --tests "com.proximityservice.verification.GeohashAccuracyTest"
```

## 출력 예시

모든 테스트는 `System.out.println`으로 구조화된 결과를 출력한다:

```
============================================================
  Geohash 정확도: 50m 반경 테스트
============================================================
  "강남 맛집 A"                  : 28.3m  [반경 내 ✓]
  "강남 맛집 B"                  : 72.1m  [반경 외 ✗]
  검색 결과                      : 1개 (기대: 1) [PASS]

============================================================
  검색 레이턴시: 1,000건 데이터
============================================================
  데이터 수                      : 1,000
  측정 횟수                      : 100
  p50                            : 3.2ms
  p95                            : 8.7ms
  p99                            : 15.1ms
  타겟 (p95 <= 100ms)            : PASS
```

## 핵심 검증 항목

| # | 검증 항목 | 테스트 위치 |
|---|----------|-----------|
| 1 | Geohash 반경 정확도 (50m/500m/20km) | GeohashAccuracyTest |
| 2 | Redis GEODIST vs Haversine 1% 이내 | GeohashAccuracyTest |
| 3 | 검색 Read 경로 = Redis | ReadWriteSeparationTest |
| 4 | 생성/삭제 Write 경로 = MySQL + Redis | ReadWriteSeparationTest |
| 5 | CRUD 후 이중 저장소 정합 | DualStorageConsistencyTest |
| 6 | Redis에 ID+좌표만 저장 | CachingStrategyTest |
| 7 | fullSync 재구축 + consistencyCheck 수정 | SyncRecoveryVerificationTest |
| 8 | 검색 p95 ≤ 100ms (1K건) | SearchLatencyTest |
| 9 | fullSync 1K건 ≤ 10초 | BatchProcessingPerformanceTest |
| 10 | 동시 검색 무에러 | ConcurrentSearchTest |
| 11 | 사업장 생애주기 E2E | BusinessLifecycleScenarioTest |
| 12 | Redis 장애→복구 E2E | SyncRecoveryScenarioTest |
