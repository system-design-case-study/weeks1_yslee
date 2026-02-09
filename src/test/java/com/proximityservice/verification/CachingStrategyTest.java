package com.proximityservice.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("캐싱 전략 검증 (Redis = 좌표 + ID만)")
class CachingStrategyTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Redis ZSet member = UUID만 (이름/주소 없음)")
    void redisZSetMemberShouldBeBusinessIdOnly() {
        Business created = createAndPersist(TestDataFactory.gangnamBusiness("이름이 긴 식당입니다"));

        Set<String> members = getRedisMembers();

        printHeader("Redis ZSet member 형식 검증");
        for (String member : members) {
            printResult("member", member);
            printResult("UUID 형식", member.matches("[0-9a-f\\-]{36}") ? "O" : "X");
            printResult("이름 포함?", member.contains("이름이") ? "X (비정상)" : "O (정상)");
        }

        assertThat(members).hasSize(1);
        String member = members.iterator().next();
        assertThat(member).isEqualTo(created.getId());
        assertThat(member).matches("[0-9a-f\\-]{36}");
        assertThat(member).doesNotContain("이름이");
        assertThat(member).doesNotContain("서울시");
    }

    @Test
    @DisplayName("Redis ZSet score = geohash 정수값")
    void redisShouldStoreGeohashScore() {
        Business created = createAndPersist(TestDataFactory.gangnamBusiness("스코어 테스트"));

        Double score = redisTemplate.opsForZSet().score("geo:businesses", created.getId());

        printHeader("Redis ZSet score (geohash) 검증");
        printResult("business ID", created.getId());
        printResult("score (geohash)", score);
        printPassFail("score != null", score != null);

        assertThat(score).isNotNull();
        assertThat(score).isGreaterThan(0);
    }

    @Test
    @DisplayName("검색 결과에 name, address, category 포함 → MySQL 조회 증명")
    void searchReturnFullDetailsFromMySQL() {
        createAndPersist(new com.proximityservice.dto.BusinessCreateRequest(
                "상세정보 식당", "서울시 강남구 테헤란로 100",
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG,
                "korean_food", "02-1234-5678", "11:00-22:00"));

        NearbySearchResponse response = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 1000);

        printHeader("검색 결과 상세정보 (MySQL 조회) 검증");
        var biz = response.businesses().get(0);
        printResult("name", biz.name());
        printResult("address", biz.address());
        printResult("category", biz.category());
        printPassFail("상세정보 포함",
                biz.name() != null && biz.address() != null && biz.category() != null);

        assertThat(biz.name()).isEqualTo("상세정보 식당");
        assertThat(biz.address()).isEqualTo("서울시 강남구 테헤란로 100");
        assertThat(biz.category()).isEqualTo("korean_food");
    }

    @Test
    @DisplayName("Redis에만 있는 고아 ID는 검색 결과에서 제외")
    void orphanIdInRedisFilteredOutFromResults() {
        // 정상 데이터 1건
        Business normal = createAndPersist(TestDataFactory.gangnamBusiness("정상 업체"));
        // 고아 데이터 (Redis에만 추가)
        geoRepository.add("orphan-uuid-1234", TestDataFactory.GANGNAM_LNG, TestDataFactory.GANGNAM_LAT);

        Set<String> redisMembers = getRedisMembers();
        NearbySearchResponse response = nearbySearchService.searchNearby(
                TestDataFactory.GANGNAM_LAT, TestDataFactory.GANGNAM_LNG, 1000);

        printHeader("고아 ID 필터링 검증");
        printResult("Redis member 수", redisMembers.size());
        printResult("검색 결과 수", response.total());
        printResult("고아 ID 포함?", response.businesses().stream()
                .anyMatch(b -> b.id().equals("orphan-uuid-1234")) ? "X (비정상)" : "O (정상)");

        assertThat(redisMembers).hasSize(2); // normal + orphan
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.businesses().get(0).id()).isEqualTo(normal.getId());
    }
}
