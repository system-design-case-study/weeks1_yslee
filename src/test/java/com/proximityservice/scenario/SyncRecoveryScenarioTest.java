package com.proximityservice.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.dto.SyncBatchResult;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("E2E: Redis 장애 → 복구 시나리오")
class SyncRecoveryScenarioTest extends BaseIntegrationTest {

    @Test
    @DisplayName("10개 생성 → Redis DEL(장애) → 검색 0건 → fullSync → 전부 복구")
    void redisFailureAndRecoveryScenario() {
        printHeader("E2E: Redis 장애 → fullSync 복구 시나리오");

        // Step 1: 10개 업체 생성
        List<Business> businesses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Business b = createAndPersist(TestDataFactory.businessAt(
                    "업체_" + (i + 1),
                    TestDataFactory.GANGNAM_LAT + (i * 0.001),
                    TestDataFactory.GANGNAM_LNG,
                    "korean_food"));
            businesses.add(b);
        }

        // 전체 검색 확인
        NearbySearchResponse beforeFailure = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT + 0.005, TestDataFactory.GANGNAM_LNG, 20000);
        printResult("Step 1: 10개 생성", beforeFailure.total() + "건 검색 가능");
        assertThat(beforeFailure.total()).isEqualTo(10);

        // Step 2: Redis 장애 시뮬레이션 (DEL)
        geoRepository.deleteAll();
        NearbySearchResponse duringFailure = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT + 0.005, TestDataFactory.GANGNAM_LNG, 20000);
        printResult("Step 2: Redis DEL (장애)", duringFailure.total() + "건 (검색 불가)");
        assertThat(duringFailure.total()).isEqualTo(0);

        // Step 3: fullSync 복구
        SyncBatchResult result = syncBatchService.fullSync();
        printResult("Step 3: fullSync 실행", String.format("added=%d, errors=%d", result.added(), result.errors()));

        // Step 4: 검색 복구 확인
        NearbySearchResponse afterRecovery = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT + 0.005, TestDataFactory.GANGNAM_LNG, 20000);
        printResult("Step 4: 복구 후 검색", afterRecovery.total() + "건 (전체 복구)");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.added()).isEqualTo(10);
        assertThat(afterRecovery.total()).isEqualTo(10);

        // 모든 업체 ID가 복구됐는지 확인
        List<String> recoveredIds = afterRecovery.businesses().stream()
                .map(b -> b.id()).toList();
        for (Business original : businesses) {
            assertThat(recoveredIds).contains(original.getId());
        }
        printPass("Redis 장애 → fullSync 복구 완료");
    }
}
