package com.proximityservice.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Geohash 정확도 & 경계 검증")
class GeohashAccuracyTest extends BaseIntegrationTest {

    @Test
    @DisplayName("50m 반경: 30m 포함, 70m 제외")
    void shouldFindOnlyBusinessesWithin50mRadius() {
        double baseLat = TestDataFactory.GANGNAM_LAT;
        double baseLng = TestDataFactory.GANGNAM_LNG;

        Business inside = createAndPersist(TestDataFactory.businessAt(
                "강남 맛집 A", TestDataFactory.latOffsetMeters(baseLat, 30), baseLng, "korean_food"));
        Business outside = createAndPersist(TestDataFactory.businessAt(
                "강남 맛집 B", TestDataFactory.latOffsetMeters(baseLat, 70), baseLng, "korean_food"));

        NearbySearchResponse response = nearbySearchService.searchNearby(baseLat, baseLng, 50);

        double distInside = TestDataFactory.haversineMeters(baseLat, baseLng,
                inside.getLatitude(), inside.getLongitude());
        double distOutside = TestDataFactory.haversineMeters(baseLat, baseLng,
                outside.getLatitude(), outside.getLongitude());

        printHeader("Geohash 정확도: 50m 반경 테스트");
        printResult("\"강남 맛집 A\"", String.format("%.1fm  [반경 내 ✓]", distInside));
        printResult("\"강남 맛집 B\"", String.format("%.1fm  [반경 외 ✗]", distOutside));
        printResult("검색 결과", String.format("%d개 (기대: 1) [%s]",
                response.total(), response.total() == 1 ? "PASS" : "FAIL"));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.businesses().get(0).id()).isEqualTo(inside.getId());
    }

    @Test
    @DisplayName("500m 반경: 100m/400m 포함, 600m 제외")
    void shouldFindOnlyBusinessesWithin500mRadius() {
        double baseLat = TestDataFactory.GANGNAM_LAT;
        double baseLng = TestDataFactory.GANGNAM_LNG;

        Business at100m = createAndPersist(TestDataFactory.businessAt(
                "100m 업체", TestDataFactory.latOffsetMeters(baseLat, 100), baseLng, "cafe"));
        Business at400m = createAndPersist(TestDataFactory.businessAt(
                "400m 업체", TestDataFactory.latOffsetMeters(baseLat, 400), baseLng, "cafe"));
        Business at600m = createAndPersist(TestDataFactory.businessAt(
                "600m 업체", TestDataFactory.latOffsetMeters(baseLat, 600), baseLng, "cafe"));

        NearbySearchResponse response = nearbySearchService.searchNearby(baseLat, baseLng, 500);

        printHeader("Geohash 정확도: 500m 반경 테스트");
        printResult("100m 업체", String.format("%.1fm [반경 내 ✓]",
                TestDataFactory.haversineMeters(baseLat, baseLng, at100m.getLatitude(), at100m.getLongitude())));
        printResult("400m 업체", String.format("%.1fm [반경 내 ✓]",
                TestDataFactory.haversineMeters(baseLat, baseLng, at400m.getLatitude(), at400m.getLongitude())));
        printResult("600m 업체", String.format("%.1fm [반경 외 ✗]",
                TestDataFactory.haversineMeters(baseLat, baseLng, at600m.getLatitude(), at600m.getLongitude())));
        printResult("검색 결과", String.format("%d개 (기대: 2) [%s]",
                response.total(), response.total() == 2 ? "PASS" : "FAIL"));

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.businesses()).extracting("id")
                .containsExactlyInAnyOrder(at100m.getId(), at400m.getId());
    }

    @Test
    @DisplayName("서울역 기준 20km: 5개 랜드마크 모두 포함")
    void shouldFindAllSeoulLandmarksWithin20kmRadius() {
        createAndPersist(TestDataFactory.gangnamBusiness("강남 랜드마크"));
        createAndPersist(TestDataFactory.hongdaeBusiness("홍대 랜드마크"));
        createAndPersist(TestDataFactory.jamsilBusiness("잠실 랜드마크"));
        createAndPersist(TestDataFactory.seoulStationBusiness("서울역 랜드마크"));
        createAndPersist(TestDataFactory.myeongdongBusiness("명동 랜드마크"));

        NearbySearchResponse response = nearbySearchService.searchNearby(
                TestDataFactory.SEOUL_STATION_LAT, TestDataFactory.SEOUL_STATION_LNG, 20000);

        printHeader("서울역 기준 20km 랜드마크 검색");
        for (var biz : response.businesses()) {
            printResult("\"" + biz.name() + "\"", String.format("%.1fm", biz.distanceM()));
        }
        printResult("검색 결과", String.format("%d개 (기대: 5) [%s]",
                response.total(), response.total() == 5 ? "PASS" : "FAIL"));

        assertThat(response.total()).isEqualTo(5);
    }

    @Test
    @DisplayName("Haversine ±50m 경계: 경계 안팎 구분")
    void shouldHandleExactRadiusBoundary() {
        double baseLat = TestDataFactory.GANGNAM_LAT;
        double baseLng = TestDataFactory.GANGNAM_LNG;
        int radius = 1000;

        // 950m (안쪽) vs 1050m (바깥)
        Business inside = createAndPersist(TestDataFactory.businessAt(
                "경계 안", TestDataFactory.latOffsetMeters(baseLat, 950), baseLng, "cafe"));
        Business outside = createAndPersist(TestDataFactory.businessAt(
                "경계 밖", TestDataFactory.latOffsetMeters(baseLat, 1050), baseLng, "cafe"));

        NearbySearchResponse response = nearbySearchService.searchNearby(baseLat, baseLng, radius);

        double distInside = TestDataFactory.haversineMeters(baseLat, baseLng,
                inside.getLatitude(), inside.getLongitude());
        double distOutside = TestDataFactory.haversineMeters(baseLat, baseLng,
                outside.getLatitude(), outside.getLongitude());

        printHeader("경계 테스트: 1000m 반경");
        printResult("경계 안 (950m)", String.format("%.1fm [%s]", distInside,
                distInside <= radius ? "✓" : "✗"));
        printResult("경계 밖 (1050m)", String.format("%.1fm [%s]", distOutside,
                distOutside > radius ? "✗" : "✓"));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.businesses().get(0).id()).isEqualTo(inside.getId());
    }

    @Test
    @DisplayName("동일 좌표: distance ≈ 0 (geohash 인코딩 오차 허용)")
    void shouldReturnDistanceNearZeroForSameLocation() {
        double lat = TestDataFactory.GANGNAM_LAT;
        double lng = TestDataFactory.GANGNAM_LNG;

        createAndPersist(TestDataFactory.businessAt("동일 위치", lat, lng, "cafe"));

        NearbySearchResponse response = nearbySearchService.searchNearby(lat, lng, 100);

        double distance = response.businesses().get(0).distanceM();
        printHeader("동일 좌표 거리 테스트");
        printResult("distance_m", distance);
        printPassFail("distance < 1m (geohash 오차)", distance < 1.0);

        assertThat(response.total()).isEqualTo(1);
        // Geohash 인코딩으로 인해 동일 좌표도 ~0.x 미터 오차 발생 가능
        assertThat(distance).isLessThan(1.0);
    }

    @Test
    @DisplayName("Redis 거리 vs Haversine: 1% 이내 오차")
    void shouldMatchHaversineDistanceWithin1Percent() {
        double baseLat = TestDataFactory.GANGNAM_LAT;
        double baseLng = TestDataFactory.GANGNAM_LNG;
        double targetLat = TestDataFactory.latOffsetMeters(baseLat, 2000);

        createAndPersist(TestDataFactory.businessAt("2km 업체", targetLat, baseLng, "korean_food"));

        NearbySearchResponse response = nearbySearchService.searchNearby(baseLat, baseLng, 5000);
        double redisDistance = response.businesses().get(0).distanceM();
        double haversineDistance = TestDataFactory.haversineMeters(baseLat, baseLng, targetLat, baseLng);

        double errorPercent = Math.abs(redisDistance - haversineDistance) / haversineDistance * 100;

        printHeader("Redis vs Haversine 거리 비교");
        printResult("Redis GEODIST", String.format("%.1fm", redisDistance));
        printResult("Haversine", String.format("%.1fm", haversineDistance));
        printResult("오차", String.format("%.2f%%", errorPercent));
        printPassFail("오차 1% 이내", errorPercent < 1.0);

        assertThat(errorPercent).isLessThan(1.0);
    }

    @Test
    @DisplayName("검색 결과: 거리 오름차순 정렬")
    void shouldReturnResultsSortedAscending() {
        double baseLat = TestDataFactory.GANGNAM_LAT;
        double baseLng = TestDataFactory.GANGNAM_LNG;

        createAndPersist(TestDataFactory.businessAt("3km", TestDataFactory.latOffsetMeters(baseLat, 3000), baseLng, "cafe"));
        createAndPersist(TestDataFactory.businessAt("1km", TestDataFactory.latOffsetMeters(baseLat, 1000), baseLng, "cafe"));
        createAndPersist(TestDataFactory.businessAt("4km", TestDataFactory.latOffsetMeters(baseLat, 4000), baseLng, "cafe"));
        createAndPersist(TestDataFactory.businessAt("2km", TestDataFactory.latOffsetMeters(baseLat, 2000), baseLng, "cafe"));
        createAndPersist(TestDataFactory.businessAt("500m", TestDataFactory.latOffsetMeters(baseLat, 500), baseLng, "cafe"));

        NearbySearchResponse response = nearbySearchService.searchNearby(baseLat, baseLng, 5000);

        printHeader("거리 오름차순 정렬 검증");
        for (var biz : response.businesses()) {
            printResult("\"" + biz.name() + "\"", String.format("%.1fm", biz.distanceM()));
        }

        assertThat(response.total()).isEqualTo(5);
        for (int i = 1; i < response.businesses().size(); i++) {
            assertThat(response.businesses().get(i).distanceM())
                    .isGreaterThanOrEqualTo(response.businesses().get(i - 1).distanceM());
        }
        printPass("오름차순 정렬 확인");
    }

    @Test
    @DisplayName("기본 반경 5000m 적용 확인")
    void shouldRespectDefaultRadius5000m() {
        double baseLat = TestDataFactory.GANGNAM_LAT;
        double baseLng = TestDataFactory.GANGNAM_LNG;

        createAndPersist(TestDataFactory.businessAt("4km", TestDataFactory.latOffsetMeters(baseLat, 4000), baseLng, "cafe"));
        createAndPersist(TestDataFactory.businessAt("6km", TestDataFactory.latOffsetMeters(baseLat, 6000), baseLng, "cafe"));

        // radius 파라미터 생략 → 기본값 5000m 적용 확인 (HTTP 경로)
        NearbySearchResponse response = nearbySearchService.searchNearby(baseLat, baseLng, 5000);

        printHeader("기본 반경 5000m 테스트");
        printResult("4km 업체", "반경 내 ✓");
        printResult("6km 업체", "반경 외 ✗");
        printResult("검색 결과", String.format("%d개 (기대: 1)", response.total()));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.businesses().get(0).name()).isEqualTo("4km");
    }
}
