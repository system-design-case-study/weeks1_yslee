package com.proximityservice.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("E2E: 서울 다중 위치 검색 시나리오")
class MultiLocationSearchScenarioTest extends BaseIntegrationTest {

    @Test
    @DisplayName("5개 랜드마크 + 강남 클러스터 → 위치별 검색 + 거리 매트릭스")
    void multiLocationSearchWithDistanceMatrix() {
        printHeader("E2E: 서울 다중 위치 검색 시나리오");

        // Step 1: 5개 랜드마크 시딩
        createAndPersist(TestDataFactory.gangnamBusiness("강남 랜드마크"));
        createAndPersist(TestDataFactory.hongdaeBusiness("홍대 랜드마크"));
        createAndPersist(TestDataFactory.jamsilBusiness("잠실 랜드마크"));
        createAndPersist(TestDataFactory.seoulStationBusiness("서울역 랜드마크"));
        createAndPersist(TestDataFactory.myeongdongBusiness("명동 랜드마크"));

        // Step 2: 강남 근처 10개 추가 시딩
        List<BusinessCreateRequest> gangnamCluster = TestDataFactory.generateCluster(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 10, "cafe", 800);
        for (BusinessCreateRequest req : gangnamCluster) {
            createAndPersist(req);
        }
        printResult("Step 1-2: 시딩 완료", "랜드마크 5 + 강남클러스터 10 = 15건");

        // Step 3: 강남 1km 검색 → 강남 클러스터만
        NearbySearchResponse gangnamSearch = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 1000);
        printResult("강남 (1km)", gangnamSearch.total() + "건");
        assertThat(gangnamSearch.total()).isGreaterThanOrEqualTo(10); // 클러스터 + 랜드마크

        // Step 4: 홍대 500m → 홍대만
        NearbySearchResponse hongdaeSearch = nearbySearchService.searchNearby(
                TestDataFactory.HONGDAE_LAT, TestDataFactory.HONGDAE_LNG, 500);
        printResult("홍대 (500m)", hongdaeSearch.total() + "건");
        assertThat(hongdaeSearch.total()).isEqualTo(1);
        assertThat(hongdaeSearch.businesses().get(0).name()).isEqualTo("홍대 랜드마크");

        // Step 5: 서울역 3km → 서울역 + 명동 (가까운 곳)
        NearbySearchResponse seoulStationSearch = nearbySearchService.searchNearby(
                TestDataFactory.SEOUL_STATION_LAT, TestDataFactory.SEOUL_STATION_LNG, 3000);
        printResult("서울역 (3km)", seoulStationSearch.total() + "건");
        List<String> seoulStationNames = seoulStationSearch.businesses().stream()
                .map(b -> b.name()).toList();
        assertThat(seoulStationNames).contains("서울역 랜드마크");
        assertThat(seoulStationNames).contains("명동 랜드마크"); // 서울역↔명동 ~2km

        // Step 6: 서울 중심 20km → 전부
        NearbySearchResponse allSearch = nearbySearchService.searchNearby(
                TestDataFactory.SEOUL_CENTER_LAT, TestDataFactory.SEOUL_CENTER_LNG, 20000);
        printResult("서울 중심 (20km)", allSearch.total() + "건");
        assertThat(allSearch.total()).isEqualTo(15);

        // Step 7: 거리 매트릭스 출력
        printDistanceMatrix();
        printPass("다중 위치 검색 시나리오 완료");
    }

    private void printDistanceMatrix() {
        String[] names = {"강남", "홍대", "잠실", "서울역", "명동"};
        double[][] coords = {
                {TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG},
                {TestDataFactory.HONGDAE_LAT, TestDataFactory.HONGDAE_LNG},
                {TestDataFactory.JAMSIL_LAT, TestDataFactory.JAMSIL_LNG},
                {TestDataFactory.SEOUL_STATION_LAT, TestDataFactory.SEOUL_STATION_LNG},
                {TestDataFactory.MYEONGDONG_LAT, TestDataFactory.MYEONGDONG_LNG}
        };

        System.out.println();
        System.out.println("============================================================");
        System.out.println("  서울 랜드마크 거리 매트릭스 (미터)");
        System.out.println("============================================================");

        // 헤더
        System.out.printf("  %8s", "");
        for (String name : names) {
            System.out.printf("%10s", name);
        }
        System.out.println();

        // 행
        for (int i = 0; i < names.length; i++) {
            System.out.printf("  %-8s", names[i]);
            for (int j = 0; j < names.length; j++) {
                if (i == j) {
                    System.out.printf("%10s", "-");
                } else {
                    double dist = TestDataFactory.haversineMeters(
                            coords[i][0], coords[i][1], coords[j][0], coords[j][1]);
                    System.out.printf("%,10.0f", dist);
                }
            }
            System.out.println();
        }
    }
}
