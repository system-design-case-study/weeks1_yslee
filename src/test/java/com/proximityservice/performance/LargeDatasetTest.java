package com.proximityservice.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("performance")
@DisplayName("대량 데이터셋 처리 테스트")
class LargeDatasetTest extends BaseIntegrationTest {

    @Test
    @DisplayName("10,000건 시딩 → MySQL/Redis 카운트 일치")
    void largeDatasetShouldMaintainConsistency() {
        int count = 10000;
        List<BusinessCreateRequest> requests = TestDataFactory.generateBulk(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, count, 20000);

        long seedStart = System.currentTimeMillis();
        for (BusinessCreateRequest req : requests) {
            businessService.create(req);
        }
        long seedDuration = System.currentTimeMillis() - seedStart;

        long mysqlCount = businessRepository.count();
        long redisCount = getRedisMemberCount();

        printHeader("대량 데이터: 10,000건 시딩");
        printResult("시딩 소요시간", seedDuration + "ms");
        printResult("MySQL count", mysqlCount);
        printResult("Redis count", redisCount);
        printPassFail("카운트 일치", mysqlCount == count && redisCount == count);

        assertThat(mysqlCount).isEqualTo(count);
        assertThat(redisCount).isEqualTo(count);
    }

    @Test
    @DisplayName("반경별 결과 수 단조증가: 1000m < 5000m < 20000m")
    void resultCountShouldIncreaseMonotonicallyWithRadius() {
        List<BusinessCreateRequest> requests = TestDataFactory.generateBulk(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 10000, 20000);
        for (BusinessCreateRequest req : requests) {
            businessService.create(req);
        }

        NearbySearchResponse r1000 = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 1000);
        NearbySearchResponse r5000 = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 5000);
        NearbySearchResponse r20000 = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 20000);

        printHeader("반경별 결과 수 단조증가");
        printResult("1,000m 반경", r1000.total() + "건");
        printResult("5,000m 반경", r5000.total() + "건");
        printResult("20,000m 반경", r20000.total() + "건");
        printPassFail("단조증가", r1000.total() <= r5000.total() && r5000.total() <= r20000.total());

        assertThat(r1000.total()).isLessThanOrEqualTo(r5000.total());
        assertThat(r5000.total()).isLessThanOrEqualTo(r20000.total());
        assertThat(r20000.total()).isGreaterThan(0);
    }

    @Test
    @DisplayName("대량 결과에서도 거리 정렬 정확")
    void largeResultShouldBeSortedByDistance() {
        List<BusinessCreateRequest> requests = TestDataFactory.generateBulk(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 10000, 20000);
        for (BusinessCreateRequest req : requests) {
            businessService.create(req);
        }

        NearbySearchResponse response = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 20000);

        boolean sorted = true;
        for (int i = 1; i < response.businesses().size(); i++) {
            if (response.businesses().get(i).distanceM() < response.businesses().get(i - 1).distanceM()) {
                sorted = false;
                break;
            }
        }

        printHeader("대량 결과 거리 정렬 검증");
        printResult("총 결과 수", response.total());
        if (response.total() > 0) {
            printResult("첫 번째 거리", response.businesses().get(0).distanceM() + "m");
            printResult("마지막 거리", response.businesses().get(response.total() - 1).distanceM() + "m");
        }
        printPassFail("오름차순 정렬", sorted);

        assertThat(sorted).isTrue();
    }
}
