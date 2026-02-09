package com.proximityservice.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("performance")
@DisplayName("검색 레이턴시 측정 (p50/p95/p99)")
class SearchLatencyTest extends BaseIntegrationTest {

    private static final int WARMUP = 10;
    private static final int ITERATIONS = 100;

    @Test
    @DisplayName("100건 데이터: p95 <= 100ms")
    void searchLatencyWith100Records() {
        seedData(100, 5000);
        long[] latencies = measureSearch(ITERATIONS, TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 5000);
        printLatencyReport("100건 데이터", 100, latencies, 100);

        assertThat(percentile(latencies, 95)).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("1,000건 데이터: p95 <= 100ms")
    void searchLatencyWith1000Records() {
        seedData(1000, 10000);
        long[] latencies = measureSearch(ITERATIONS, TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 10000);
        printLatencyReport("1,000건 데이터", 1000, latencies, 100);

        assertThat(percentile(latencies, 95)).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("10,000건 데이터: p95 <= 200ms")
    void searchLatencyWith10000Records() {
        seedData(10000, 20000);
        long[] latencies = measureSearch(ITERATIONS, TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 20000);
        printLatencyReport("10,000건 데이터", 10000, latencies, 200);

        assertThat(percentile(latencies, 95)).isLessThanOrEqualTo(200);
    }

    private void seedData(int count, double radiusMeters) {
        List<BusinessCreateRequest> requests = TestDataFactory.generateBulk(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, count, radiusMeters);
        for (BusinessCreateRequest req : requests) {
            businessService.create(req);
        }
    }

    private long[] measureSearch(int iterations, double lat, double lng, int radius) {
        // 워밍업
        for (int i = 0; i < WARMUP; i++) {
            nearbySearchService.searchNearby(lat, lng, radius);
        }

        long[] latencies = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            nearbySearchService.searchNearby(lat, lng, radius);
            latencies[i] = (System.nanoTime() - start) / 1_000_000; // ms
        }
        Arrays.sort(latencies);
        return latencies;
    }

    private long percentile(long[] sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    private void printLatencyReport(String label, int dataCount, long[] sorted, long targetP95) {
        printHeader("검색 레이턴시: " + label);
        printResult("데이터 수", String.format("%,d", dataCount));
        printResult("측정 횟수", ITERATIONS);
        printResult("p50", sorted[sorted.length / 2] + "ms");
        printResult("p95", percentile(sorted, 95) + "ms");
        printResult("p99", percentile(sorted, 99) + "ms");
        printResult("min", sorted[0] + "ms");
        printResult("max", sorted[sorted.length - 1] + "ms");
        printPassFail("타겟 (p95 <= " + targetP95 + "ms)",
                percentile(sorted, 95) <= targetP95);
    }
}
