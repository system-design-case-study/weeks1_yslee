package com.proximityservice.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.SyncBatchResult;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("E2E: 불일치 감지 → consistencyCheck 수정 시나리오")
class ConsistencyCheckScenarioTest extends BaseIntegrationTest {

    @Test
    @DisplayName("5개 생성 → Redis 수동 변조 → 불일치 → consistencyCheck → 정합 복원")
    void inconsistencyDetectionAndRepairScenario() {
        printHeader("E2E: 불일치 감지 → consistencyCheck 시나리오");

        // Step 1: 5개 정상 생성
        List<Business> businesses = new ArrayList<>();
        businesses.add(createAndPersist(TestDataFactory.gangnamBusiness("강남 업체")));
        businesses.add(createAndPersist(TestDataFactory.hongdaeBusiness("홍대 업체")));
        businesses.add(createAndPersist(TestDataFactory.jamsilBusiness("잠실 업체")));
        businesses.add(createAndPersist(TestDataFactory.seoulStationBusiness("서울역 업체")));
        businesses.add(createAndPersist(TestDataFactory.myeongdongBusiness("명동 업체")));

        long mysqlBefore = businessRepository.count();
        long redisBefore = getRedisMemberCount();
        printResult("Step 1: 5개 생성", String.format("MySQL=%d, Redis=%d", mysqlBefore, redisBefore));
        assertThat(mysqlBefore).isEqualTo(5);
        assertThat(redisBefore).isEqualTo(5);

        // Step 2: Redis에서 1개 수동 삭제 (missing 발생)
        String removedId = businesses.get(0).getId();
        geoRepository.remove(removedId);
        printResult("Step 2: Redis에서 수동 삭제", removedId.substring(0, 8) + "...");

        // Step 3: Redis에 고아 2개 추가
        geoRepository.add("orphan-aaa", 127.0, 37.5);
        geoRepository.add("orphan-bbb", 127.1, 37.6);
        printResult("Step 3: 고아 2개 추가", "orphan-aaa, orphan-bbb");

        // Step 4: 불일치 상태 확인
        long mysqlMid = businessRepository.count();
        long redisMid = getRedisMemberCount();
        Set<String> mysqlIds = businessRepository.findAll().stream()
                .map(Business::getId).collect(Collectors.toSet());
        Set<String> redisIds = getRedisMembers();

        printResult("Step 4: 불일치 상태", String.format("MySQL=%d, Redis=%d", mysqlMid, redisMid));
        assertThat(mysqlMid).isEqualTo(5);
        assertThat(redisMid).isEqualTo(6); // 4 real + 2 orphan

        // Step 5: consistencyCheck 실행
        SyncBatchResult result = syncBatchService.consistencyCheck();
        printResult("Step 5: consistencyCheck", String.format("added=%d, removed=%d", result.added(), result.removed()));

        // Step 6: 정합 상태 확인
        long mysqlAfter = businessRepository.count();
        long redisAfter = getRedisMemberCount();
        Set<String> mysqlFinalIds = businessRepository.findAll().stream()
                .map(Business::getId).collect(Collectors.toSet());
        Set<String> redisFinalIds = getRedisMembers();

        printResult("Step 6: 정합 상태", String.format("MySQL=%d, Redis=%d", mysqlAfter, redisAfter));
        printResult("ID 일치?", mysqlFinalIds.equals(redisFinalIds) ? "O" : "X");

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.added()).isEqualTo(1);  // missing 1건 추가
        assertThat(result.removed()).isEqualTo(2); // orphan 2건 제거
        assertThat(mysqlAfter).isEqualTo(5);
        assertThat(redisAfter).isEqualTo(5);
        assertThat(redisFinalIds).containsExactlyInAnyOrderElementsOf(mysqlFinalIds);
        printPass("불일치 감지 → 수정 완료");
    }
}
