# Quickstart: k6 부하 테스트 + Prometheus/Grafana 시각화

## 사전 조건

- Phase 1~4 완료
- Docker + Docker Compose 설치
- 포트 3000 (Grafana), 9090 (Prometheus) 사용 가능

## 전체 스택 시작

```bash
# 앱 + MySQL + Redis + Prometheus + Grafana 일괄 시작
docker compose up -d
```

서비스별 상태 확인:
- 앱: http://localhost:8080/actuator/health
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

## 부하 테스트 실행

### 주변 검색 부하 테스트

```bash
docker compose --profile test run --rm k6 run /scripts/nearby-search.js
```

### CRUD 혼합 부하 테스트

```bash
docker compose --profile test run --rm k6 run /scripts/crud-mixed.js
```

### 특정 시나리오 커스텀 실행

```bash
# VU 수와 시간을 오버라이드
docker compose --profile test run --rm \
  -e K6_VUS=100 \
  -e K6_DURATION=60s \
  k6 run /scripts/nearby-search.js
```

## 결과 확인

### 터미널 출력 (k6 기본)

```
     ✓ status is 200

     checks.........................: 100.00% ✓ 4823  ✗ 0
     http_req_duration..............: avg=12.3ms  min=2.1ms  med=8.7ms  max=142ms  p(90)=23ms  p(95)=45ms
     http_req_failed................: 0.00%   ✓ 0     ✗ 4823
     http_reqs......................: 4823    160.7/s
     vus............................: 50      min=10  max=50
     vus_max........................: 50      min=50  max=50
```

### Grafana 대시보드 (시각화)

1. 브라우저에서 http://localhost:3000 접속
2. 사전 구성된 "Proximity Service - Load Test" 대시보드 선택
3. 실시간 그래프 확인:
   - HTTP 레이턴시 (p50/p95/p99)
   - 초당 요청 수 (RPS)
   - 에러율
   - JVM 힙 메모리
   - HikariCP 커넥션 풀
   - k6 가상 사용자 수

### Prometheus 직접 쿼리 (고급)

```
# HTTP 요청 p95 레이턴시
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))

# 초당 요청 수
rate(http_server_requests_seconds_count[1m])

# k6 VU 수
k6_vus
```

## 정리

```bash
# 전체 스택 중지 + 볼륨 제거
docker compose down -v
```

## 핵심 검증 항목

| # | 검증 항목 | 확인 방법 |
|---|----------|----------|
| 1 | k6 주변 검색 부하 테스트 실행 | 터미널에 p95, RPS 출력 확인 |
| 2 | k6 CRUD 혼합 부하 테스트 실행 | 에러율 < 1% 확인 |
| 3 | Prometheus 메트릭 수집 | http://localhost:9090 → `http_server_requests_seconds_count` 쿼리 |
| 4 | Grafana 대시보드 시각화 | http://localhost:3000 → 패널 7개 그래프 확인 |
| 5 | 원클릭 통합 실행 | `docker compose up -d` 후 모든 서비스 정상 |
| 6 | k6 메트릭 Grafana 반영 | 부하 테스트 실행 중 대시보드에서 실시간 그래프 업데이트 확인 |
