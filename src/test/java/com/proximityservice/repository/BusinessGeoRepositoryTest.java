package com.proximityservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class BusinessGeoRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("proximity");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Autowired
    private BusinessGeoRepository geoRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("geo:businesses");
    }

    @Test
    void shouldAddAndSearchBusiness() {
        geoRepository.add("biz-1", 127.0396, 37.5012);
        geoRepository.add("biz-2", 127.0380, 37.5025);

        GeoResults<GeoLocation<String>> results =
                geoRepository.searchNearby(127.0396, 37.5012, 1000, 20);

        assertThat(results).isNotNull();
        assertThat(results.getContent()).hasSize(2);
        assertThat(results.getContent().get(0).getContent().getName()).isEqualTo("biz-1");
    }

    @Test
    void shouldReturnEmptyForFarAwaySearch() {
        geoRepository.add("biz-1", 127.0396, 37.5012);

        GeoResults<GeoLocation<String>> results =
                geoRepository.searchNearby(126.0, 36.0, 100, 20);

        assertThat(results).isNotNull();
        assertThat(results.getContent()).isEmpty();
    }

    @Test
    void shouldReturnResultsSortedByDistance() {
        geoRepository.add("far", 127.05, 37.51);
        geoRepository.add("near", 127.0396, 37.5013);

        GeoResults<GeoLocation<String>> results =
                geoRepository.searchNearby(127.0396, 37.5012, 5000, 20);

        assertThat(results.getContent()).hasSizeGreaterThanOrEqualTo(2);
        GeoResult<GeoLocation<String>> first = results.getContent().get(0);
        GeoResult<GeoLocation<String>> second = results.getContent().get(1);
        assertThat(first.getDistance().getValue())
                .isLessThanOrEqualTo(second.getDistance().getValue());
    }

    @Test
    void shouldRemoveBusiness() {
        geoRepository.add("biz-1", 127.0396, 37.5012);
        geoRepository.remove("biz-1");

        GeoResults<GeoLocation<String>> results =
                geoRepository.searchNearby(127.0396, 37.5012, 1000, 20);

        assertThat(results).isNotNull();
        assertThat(results.getContent()).isEmpty();
    }
}
