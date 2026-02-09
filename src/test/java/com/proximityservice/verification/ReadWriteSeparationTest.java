package com.proximityservice.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.BusinessUpdateRequest;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Read/Write 분리 경로 검증")
class ReadWriteSeparationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("검색은 Redis 경로: MySQL에만 있으면 검색 안됨")
    void searchShouldUseRedisNotMySQL() {
        // MySQL에만 저장 (Redis 동기화 없이)
        Business business = new Business("MySQL 전용", "서울시 강남구",
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, "korean_food", null, null);
        businessRepository.save(business);

        NearbySearchResponse response = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 1000);

        printHeader("Read 경로: Redis 사용 검증");
        printResult("MySQL 저장", "O");
        printResult("Redis 저장", "X");
        printResult("검색 결과", String.format("%d개 (기대: 0)", response.total()));
        printPassFail("Redis 경로 증명", response.total() == 0);

        assertThat(response.total()).isEqualTo(0);
    }

    @Test
    @DisplayName("검색 결과 필터링: Redis에만 ID 있으면 결과 제외")
    void searchShouldFilterOrphanRedisIds() {
        // Redis에만 고아 ID 추가
        geoRepository.add("orphan-id", TestDataFactory.GANGNAM_LNG, TestDataFactory.GANGNAM_LAT);

        NearbySearchResponse response = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 1000);

        printHeader("Read 경로: 고아 ID 필터링 검증");
        printResult("Redis 고아 ID", "orphan-id");
        printResult("MySQL 해당 ID", "없음");
        printResult("검색 결과", String.format("%d개 (기대: 0)", response.total()));
        printPassFail("MySQL 조회 후 필터링 증명", response.total() == 0);

        assertThat(response.total()).isEqualTo(0);
    }

    @Test
    @DisplayName("Write 경로: 생성 → MySQL + Redis 모두 저장")
    void createShouldPersistToBothStores() {
        BusinessCreateRequest request = TestDataFactory.gangnamBusiness("양쪽 저장 테스트");
        Business created = businessService.create(request);

        boolean inMySQL = businessRepository.findById(created.getId()).isPresent();
        boolean inRedis = getRedisMembers().contains(created.getId());

        printHeader("Write 경로: 이중 저장 검증");
        printResult("MySQL 존재", inMySQL ? "O" : "X");
        printResult("Redis 존재", inRedis ? "O" : "X");
        printPassFail("양쪽 모두 저장", inMySQL && inRedis);

        assertThat(inMySQL).isTrue();
        assertThat(inRedis).isTrue();
    }

    @Test
    @DisplayName("Write 경로: 삭제 → MySQL + Redis 모두 제거")
    void deleteShouldRemoveFromBothStores() {
        Business created = createAndPersist(TestDataFactory.gangnamBusiness("삭제 대상"));
        String id = created.getId();

        businessService.delete(id);

        boolean inMySQL = businessRepository.findById(id).isPresent();
        boolean inRedis = getRedisMembers().contains(id);

        printHeader("Write 경로: 이중 삭제 검증");
        printResult("MySQL 존재", inMySQL ? "O" : "X");
        printResult("Redis 존재", inRedis ? "O" : "X");
        printPassFail("양쪽 모두 삭제", !inMySQL && !inRedis);

        assertThat(inMySQL).isFalse();
        assertThat(inRedis).isFalse();
    }

    @Test
    @DisplayName("메타데이터만 변경 → Redis 변동 없음")
    void metadataUpdateShouldNotTouchRedis() {
        Business created = createAndPersist(TestDataFactory.gangnamBusiness("원래 이름"));
        Set<String> redisBefore = getRedisMembers();

        businessService.update(created.getId(), new BusinessUpdateRequest(
                "변경된 이름", "새 주소",
                created.getLatitude(), created.getLongitude(),
                "korean_food", "02-9999-9999", "09:00-21:00"));

        Set<String> redisAfter = getRedisMembers();

        printHeader("메타데이터 변경 시 Redis 무변동 검증");
        printResult("Redis before", redisBefore);
        printResult("Redis after", redisAfter);
        printPassFail("Redis 변동 없음", redisBefore.equals(redisAfter));

        assertThat(redisAfter).isEqualTo(redisBefore);

        // MySQL은 변경됨 확인
        Business updated = businessService.getById(created.getId());
        assertThat(updated.getName()).isEqualTo("변경된 이름");
    }

    @Test
    @DisplayName("좌표 변경 → Redis 업데이트, 새 위치 검색됨")
    void coordinateUpdateShouldUpdateRedis() {
        Business created = createAndPersist(TestDataFactory.gangnamBusiness("이동할 업체"));

        double newLat = TestDataFactory.HONGDAE_LAT;
        double newLng = TestDataFactory.HONGDAE_LNG;

        businessService.update(created.getId(), new BusinessUpdateRequest(
                "이동한 업체", "서울시 마포구",
                newLat, newLng, "korean_food", null, null));

        // 구 위치(강남)에서 검색
        NearbySearchResponse oldLocation = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 1000);

        // 신 위치(홍대)에서 검색
        NearbySearchResponse newLocation = nearbySearchService.searchNearby(newLat, newLng, 1000);

        printHeader("좌표 변경 → Redis 업데이트 검증");
        printResult("구 위치(강남) 검색", String.format("%d개 (기대: 0)", oldLocation.total()));
        printResult("신 위치(홍대) 검색", String.format("%d개 (기대: 1)", newLocation.total()));
        printPassFail("좌표 업데이트 완료",
                oldLocation.total() == 0 && newLocation.total() == 1);

        assertThat(oldLocation.total()).isEqualTo(0);
        assertThat(newLocation.total()).isEqualTo(1);
        assertThat(newLocation.businesses().get(0).id()).isEqualTo(created.getId());
    }
}
