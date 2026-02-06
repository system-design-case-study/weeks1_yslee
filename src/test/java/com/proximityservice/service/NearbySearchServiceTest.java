package com.proximityservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class NearbySearchServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("proximity");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Autowired
    private NearbySearchService searchService;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessGeoRepository geoRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        businessRepository.deleteAll();
        redisTemplate.delete("geo:businesses");

        Business b1 = new Business("맛있는 식당", "서울시 강남구 테헤란로 123",
                37.5012, 127.0396, "korean_food", "02-1234-5678", "11:00-22:00");
        Business b2 = new Business("좋은 카페", "서울시 강남구 역삼로 45",
                37.5025, 127.0380, "cafe", "02-9876-5432", "08:00-23:00");

        businessRepository.save(b1);
        businessRepository.save(b2);
        geoRepository.add(b1.getId(), b1.getLongitude(), b1.getLatitude());
        geoRepository.add(b2.getId(), b2.getLongitude(), b2.getLatitude());
    }

    @Test
    void shouldReturnBusinessesSortedByDistance() {
        NearbySearchResponse response = searchService.searchNearby(37.5012, 127.0396, 1000);

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.businesses()).hasSize(2);
        assertThat(response.businesses().get(0).distanceM())
                .isLessThanOrEqualTo(response.businesses().get(1).distanceM());
    }

    @Test
    void shouldReturnOnlyBusinessesWithinRadius() {
        NearbySearchResponse response = searchService.searchNearby(37.5012, 127.0396, 50);

        assertThat(response.total()).isLessThanOrEqualTo(1);
        for (var biz : response.businesses()) {
            assertThat(biz.distanceM()).isLessThanOrEqualTo(50.0);
        }
    }

    @Test
    void shouldReturnEmptyWithMessageWhenNoResults() {
        NearbySearchResponse response = searchService.searchNearby(36.0, 126.0, 100);

        assertThat(response.total()).isZero();
        assertThat(response.businesses()).isEmpty();
        assertThat(response.message()).isNotNull();
        assertThat(response.message()).contains("반경");
    }

    @Test
    void shouldIncludeAllRequiredFields() {
        NearbySearchResponse response = searchService.searchNearby(37.5012, 127.0396, 5000);

        assertThat(response.businesses()).isNotEmpty();
        var first = response.businesses().get(0);
        assertThat(first.id()).isNotNull();
        assertThat(first.name()).isNotNull();
        assertThat(first.address()).isNotNull();
        assertThat(first.latitude()).isNotNull();
        assertThat(first.longitude()).isNotNull();
        assertThat(first.distanceM()).isNotNull();
        assertThat(first.category()).isNotNull();
    }
}
