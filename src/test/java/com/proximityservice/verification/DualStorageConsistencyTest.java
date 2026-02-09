package com.proximityservice.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.BusinessUpdateRequest;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MySQL ↔ Redis 이중 저장소 정합성 검증")
class DualStorageConsistencyTest extends BaseIntegrationTest {

    @Test
    @DisplayName("생성 후 양쪽 ID 일치")
    void afterCreateBothStoresConsistent() {
        Business created = createAndPersist(TestDataFactory.gangnamBusiness("정합성 테스트"));

        boolean inMySQL = businessRepository.findById(created.getId()).isPresent();
        boolean inRedis = getRedisMembers().contains(created.getId());

        printHeader("생성 후 이중 저장소 정합성");
        printResult("MySQL ID", created.getId());
        printResult("Redis member", inRedis ? created.getId() : "없음");
        printPassFail("MySQL ID == Redis member", inMySQL && inRedis);

        assertThat(inMySQL).isTrue();
        assertThat(inRedis).isTrue();
    }

    @Test
    @DisplayName("5개 생성 → MySQL 5건 == Redis 5건")
    void multipleCreatesAllConsistent() {
        List<String> ids = new ArrayList<>();
        ids.add(createAndPersist(TestDataFactory.gangnamBusiness("강남 1")).getId());
        ids.add(createAndPersist(TestDataFactory.hongdaeBusiness("홍대 1")).getId());
        ids.add(createAndPersist(TestDataFactory.jamsilBusiness("잠실 1")).getId());
        ids.add(createAndPersist(TestDataFactory.seoulStationBusiness("서울역 1")).getId());
        ids.add(createAndPersist(TestDataFactory.myeongdongBusiness("명동 1")).getId());

        long mysqlCount = businessRepository.count();
        Set<String> redisMembers = getRedisMembers();

        printHeader("다건 생성 정합성");
        printResult("MySQL count", mysqlCount);
        printResult("Redis count", redisMembers.size());
        printPassFail("양쪽 수 일치", mysqlCount == 5 && redisMembers.size() == 5);

        assertThat(mysqlCount).isEqualTo(5);
        assertThat(redisMembers).hasSize(5);
        assertThat(redisMembers).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    @DisplayName("좌표 변경 후 양쪽 정합")
    void afterCoordinateUpdateBothConsistent() {
        Business created = createAndPersist(TestDataFactory.gangnamBusiness("좌표 변경 대상"));

        businessService.update(created.getId(), new BusinessUpdateRequest(
                "이동한 업체", "서울시 마포구",
                TestDataFactory.HONGDAE_LAT, TestDataFactory.HONGDAE_LNG,
                "korean_food", null, null));

        boolean inMySQL = businessRepository.findById(created.getId()).isPresent();
        boolean inRedis = getRedisMembers().contains(created.getId());
        Business updated = businessService.getById(created.getId());

        printHeader("좌표 변경 후 정합성");
        printResult("MySQL 존재", inMySQL);
        printResult("Redis 존재", inRedis);
        printResult("MySQL 좌표", String.format("%.4f, %.4f", updated.getLatitude(), updated.getLongitude()));
        printPassFail("양쪽 정합", inMySQL && inRedis);

        assertThat(inMySQL).isTrue();
        assertThat(inRedis).isTrue();
        assertThat(updated.getLatitude()).isEqualTo(TestDataFactory.HONGDAE_LAT);
    }

    @Test
    @DisplayName("메타데이터만 변경해도 정합 유지")
    void afterMetadataUpdateStillConsistent() {
        Business created = createAndPersist(TestDataFactory.gangnamBusiness("메타 변경 대상"));
        Set<String> redisBefore = getRedisMembers();

        businessService.update(created.getId(), new BusinessUpdateRequest(
                "이름만 변경", created.getAddress(),
                created.getLatitude(), created.getLongitude(),
                "korean_food", "02-1111-2222", "10:00-20:00"));

        Set<String> redisAfter = getRedisMembers();

        printHeader("메타데이터 변경 후 정합성");
        printResult("Redis before", redisBefore.size() + "건");
        printResult("Redis after", redisAfter.size() + "건");
        printPassFail("정합 유지", redisBefore.equals(redisAfter));

        assertThat(redisAfter).isEqualTo(redisBefore);
    }

    @Test
    @DisplayName("삭제 후 양쪽에서 제거됨")
    void afterDeleteBothConsistent() {
        Business created = createAndPersist(TestDataFactory.gangnamBusiness("삭제 대상"));
        String id = created.getId();

        businessService.delete(id);

        boolean inMySQL = businessRepository.findById(id).isPresent();
        boolean inRedis = getRedisMembers().contains(id);

        printHeader("삭제 후 정합성");
        printResult("MySQL 존재", inMySQL);
        printResult("Redis 존재", inRedis);
        printPassFail("양쪽 삭제 완료", !inMySQL && !inRedis);

        assertThat(inMySQL).isFalse();
        assertThat(inRedis).isFalse();
    }

    @Test
    @DisplayName("1건 삭제 → 나머지 영향 없음")
    void deleteOneShouldNotAffectOthers() {
        Business b1 = createAndPersist(TestDataFactory.gangnamBusiness("유지 1"));
        Business b2 = createAndPersist(TestDataFactory.hongdaeBusiness("삭제할 업체"));
        Business b3 = createAndPersist(TestDataFactory.jamsilBusiness("유지 2"));

        businessService.delete(b2.getId());

        Set<String> mysqlIds = businessRepository.findAll().stream()
                .map(Business::getId).collect(Collectors.toSet());
        Set<String> redisIds = getRedisMembers();

        printHeader("1건 삭제 후 나머지 무영향 검증");
        printResult("MySQL 잔여", mysqlIds.size() + "건");
        printResult("Redis 잔여", redisIds.size() + "건");
        printResult("삭제된 ID 포함?", mysqlIds.contains(b2.getId()) || redisIds.contains(b2.getId()));
        printPassFail("나머지 무영향", mysqlIds.size() == 2 && redisIds.size() == 2);

        assertThat(mysqlIds).containsExactlyInAnyOrder(b1.getId(), b3.getId());
        assertThat(redisIds).containsExactlyInAnyOrder(b1.getId(), b3.getId());
    }
}
