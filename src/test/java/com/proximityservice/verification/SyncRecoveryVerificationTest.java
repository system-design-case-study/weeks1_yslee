package com.proximityservice.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.dto.SyncBatchResult;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("동기화 & 복구 메커니즘 검증")
class SyncRecoveryVerificationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("fullSync: Redis DEL → fullSync → 전체 복구")
    void fullSyncShouldRebuildFromMySQL() {
        Business b1 = createAndPersist(TestDataFactory.gangnamBusiness("강남 식당"));
        Business b2 = createAndPersist(TestDataFactory.hongdaeBusiness("홍대 카페"));
        Business b3 = createAndPersist(TestDataFactory.jamsilBusiness("잠실 바"));

        // Redis 전체 삭제 (장애 시뮬레이션)
        geoRepository.deleteAll();
        assertThat(getRedisMembers()).isEmpty();

        // fullSync 실행
        SyncBatchResult result = syncBatchService.fullSync();

        Set<String> redisMembers = getRedisMembers();

        printHeader("fullSync: Redis 재구축 검증");
        printResult("MySQL 업체 수", businessRepository.count());
        printResult("fullSync added", result.added());
        printResult("Redis 복구 후", redisMembers.size() + "건");
        printPassFail("전체 복구 성공", redisMembers.size() == 3);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.added()).isEqualTo(3);
        assertThat(redisMembers).containsExactlyInAnyOrder(b1.getId(), b2.getId(), b3.getId());
    }

    @Test
    @DisplayName("fullSync: 가짜 데이터 제거, 정상 데이터만 남음")
    void fullSyncShouldRemoveStaleData() {
        Business real = createAndPersist(TestDataFactory.gangnamBusiness("진짜 업체"));

        // 가짜 3건 추가
        geoRepository.add("fake-1", 127.0, 37.5);
        geoRepository.add("fake-2", 127.1, 37.6);
        geoRepository.add("fake-3", 127.2, 37.7);
        assertThat(getRedisMembers()).hasSize(4); // real + fake 3

        SyncBatchResult result = syncBatchService.fullSync();
        Set<String> redisMembers = getRedisMembers();

        printHeader("fullSync: 가짜 데이터 제거 검증");
        printResult("fullSync 전 Redis", "4건 (진짜1 + 가짜3)");
        printResult("fullSync 후 Redis", redisMembers.size() + "건");
        printResult("가짜 ID 포함?", redisMembers.contains("fake-1") ? "X" : "O");
        printPassFail("정상 데이터만 남음", redisMembers.size() == 1);

        assertThat(redisMembers).hasSize(1);
        assertThat(redisMembers).contains(real.getId());
        assertThat(redisMembers).doesNotContain("fake-1", "fake-2", "fake-3");
    }

    @Test
    @DisplayName("fullSync 후 좌표 정확도 유지 (distance ~0m)")
    void fullSyncShouldPreserveAccurateCoords() {
        createAndPersist(TestDataFactory.gangnamBusiness("강남 좌표 확인"));

        geoRepository.deleteAll();
        syncBatchService.fullSync();

        NearbySearchResponse response = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 100);

        printHeader("fullSync 후 좌표 정확도 검증");
        printResult("검색 결과", response.total() + "건");
        if (response.total() > 0) {
            printResult("distance_m", response.businesses().get(0).distanceM());
        }
        printPassFail("좌표 정확 (distance ~0m)",
                response.total() == 1 && response.businesses().get(0).distanceM() < 1.0);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.businesses().get(0).distanceM()).isLessThan(1.0);
    }

    @Test
    @DisplayName("consistencyCheck: MySQL에만 있는 5건 → Redis에 추가")
    void consistencyCheckShouldAddMissing() {
        // MySQL에 저장하되 Redis에는 추가하지 않음
        for (int i = 0; i < 5; i++) {
            Business b = new Business("누락 업체 " + i, "서울시",
                    TestDataFactory.GANGNAM_LAT + (i * 0.001),
                    TestDataFactory.GANGNAM_LNG, "cafe", null, null);
            businessRepository.save(b);
        }
        assertThat(getRedisMembers()).isEmpty();

        SyncBatchResult result = syncBatchService.consistencyCheck();

        printHeader("consistencyCheck: 누락 업체 추가 검증");
        printResult("MySQL 업체 수", businessRepository.count());
        printResult("check 전 Redis", "0건");
        printResult("added", result.added());
        printResult("removed", result.removed());
        printResult("check 후 Redis", getRedisMembers().size() + "건");
        printPassFail("누락 5건 추가", result.added() == 5 && result.removed() == 0);

        assertThat(result.added()).isEqualTo(5);
        assertThat(result.removed()).isEqualTo(0);
        assertThat(getRedisMembers()).hasSize(5);
    }

    @Test
    @DisplayName("consistencyCheck: 고아 2건 → Redis에서 제거")
    void consistencyCheckShouldRemoveOrphans() {
        Business real = createAndPersist(TestDataFactory.gangnamBusiness("진짜 업체"));

        // 고아 2건 추가
        geoRepository.add("orphan-1", 127.0, 37.5);
        geoRepository.add("orphan-2", 127.1, 37.6);
        assertThat(getRedisMembers()).hasSize(3);

        SyncBatchResult result = syncBatchService.consistencyCheck();

        printHeader("consistencyCheck: 고아 제거 검증");
        printResult("check 전 Redis", "3건 (진짜1 + 고아2)");
        printResult("added", result.added());
        printResult("removed", result.removed());
        printResult("check 후 Redis", getRedisMembers().size() + "건");
        printPassFail("고아 2건 제거", result.removed() == 2 && result.added() == 0);

        assertThat(result.removed()).isEqualTo(2);
        assertThat(result.added()).isEqualTo(0);
        assertThat(getRedisMembers()).containsExactly(real.getId());
    }

    @Test
    @DisplayName("consistencyCheck: missing 2 + orphan 2 → 동시 수정")
    void consistencyCheckMixedScenario() {
        // 정상 1건
        Business normal = createAndPersist(TestDataFactory.gangnamBusiness("정상 업체"));

        // MySQL에만 2건 추가 (missing)
        Business missing1 = new Business("누락 1", "서울시",
                TestDataFactory.HONGDAE_LAT, TestDataFactory.HONGDAE_LNG, "cafe", null, null);
        Business missing2 = new Business("누락 2", "서울시",
                TestDataFactory.JAMSIL_LAT, TestDataFactory.JAMSIL_LNG, "bar", null, null);
        businessRepository.save(missing1);
        businessRepository.save(missing2);

        // Redis에만 2건 추가 (orphan)
        geoRepository.add("orphan-a", 127.0, 37.5);
        geoRepository.add("orphan-b", 127.1, 37.6);

        long mysqlCount = businessRepository.count();
        long redisCount = getRedisMemberCount();

        SyncBatchResult result = syncBatchService.consistencyCheck();

        Set<String> afterRedis = getRedisMembers();

        printHeader("consistencyCheck: 혼합 시나리오 검증");
        printResult("MySQL 업체 수", mysqlCount);
        printResult("check 전 Redis", redisCount + "건");
        printResult("added (missing)", result.added());
        printResult("removed (orphan)", result.removed());
        printResult("check 후 Redis", afterRedis.size() + "건");
        printPassFail("혼합 수정 완료", result.added() == 2 && result.removed() == 2);

        assertThat(result.added()).isEqualTo(2);
        assertThat(result.removed()).isEqualTo(2);
        assertThat(afterRedis).hasSize(3);
        assertThat(afterRedis).containsExactlyInAnyOrder(
                normal.getId(), missing1.getId(), missing2.getId());
    }
}
