package com.proximityservice.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.SyncBatchResult;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("performance")
@DisplayName("배치 처리 성능 측정")
class BatchProcessingPerformanceTest extends BaseIntegrationTest {

    @Test
    @DisplayName("1,000건 fullSync ≤ 10초")
    void fullSyncWith1000RecordsShouldCompleteWithin10Seconds() {
        seedMySQLOnly(1000);

        long start = System.currentTimeMillis();
        SyncBatchResult result = syncBatchService.fullSync();
        long durationMs = System.currentTimeMillis() - start;

        printHeader("배치 처리: 1,000건 fullSync");
        printResult("데이터 수", 1000);
        printResult("처리 건수", result.totalProcessed());
        printResult("added", result.added());
        printResult("errors", result.errors());
        printResult("소요시간", durationMs + "ms");
        printPassFail("10초 이내", durationMs <= 10_000);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.added()).isEqualTo(1000);
        assertThat(durationMs).isLessThanOrEqualTo(10_000);
    }

    @Test
    @DisplayName("5,000건 fullSync ≤ 30초")
    void fullSyncWith5000RecordsShouldCompleteWithin30Seconds() {
        seedMySQLOnly(5000);

        long start = System.currentTimeMillis();
        SyncBatchResult result = syncBatchService.fullSync();
        long durationMs = System.currentTimeMillis() - start;

        printHeader("배치 처리: 5,000건 fullSync");
        printResult("데이터 수", 5000);
        printResult("처리 건수", result.totalProcessed());
        printResult("added", result.added());
        printResult("errors", result.errors());
        printResult("소요시간", durationMs + "ms");
        printPassFail("30초 이내", durationMs <= 30_000);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.added()).isEqualTo(5000);
        assertThat(durationMs).isLessThanOrEqualTo(30_000);
    }

    @Test
    @DisplayName("1,000건 consistencyCheck 소요시간 측정")
    void consistencyCheckWith1000Records() {
        // 500건은 정상, 300건 missing, 200건 orphan
        seedMySQLOnly(800);  // MySQL에 800건

        // 처음 500건만 Redis에 추가 (나머지 300건은 missing)
        List<Business> allBusinesses = businessRepository.findAll();
        for (int i = 0; i < 500; i++) {
            Business b = allBusinesses.get(i);
            geoRepository.add(b.getId(), b.getLongitude(), b.getLatitude());
        }

        // orphan 200건 추가
        for (int i = 0; i < 200; i++) {
            geoRepository.add("orphan-" + i, 127.0 + (i * 0.001), 37.5);
        }

        long start = System.currentTimeMillis();
        SyncBatchResult result = syncBatchService.consistencyCheck();
        long durationMs = System.currentTimeMillis() - start;

        printHeader("배치 처리: 1,000건 consistencyCheck");
        printResult("MySQL 업체 수", 800);
        printResult("Redis 초기 (정상+고아)", "500 + 200 = 700건");
        printResult("added (missing)", result.added());
        printResult("removed (orphan)", result.removed());
        printResult("소요시간", durationMs + "ms");
        printResult("Redis 최종", getRedisMembers().size() + "건");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.added()).isEqualTo(300);
        assertThat(result.removed()).isEqualTo(200);
    }

    private void seedMySQLOnly(int count) {
        List<Business> businesses = new ArrayList<>();
        java.util.Random random = new java.util.Random(42);

        for (int i = 0; i < count; i++) {
            double lat = 37.4 + random.nextDouble() * 0.3;
            double lng = 126.8 + random.nextDouble() * 0.4;
            String category = switch (i % 5) {
                case 0 -> "korean_food";
                case 1 -> "cafe";
                case 2 -> "bar";
                case 3 -> "convenience";
                default -> "pharmacy";
            };
            businesses.add(new Business("업체_" + i, "서울시 테스트 " + i,
                    lat, lng, category, null, null));
        }
        businessRepository.saveAll(businesses);
    }
}
