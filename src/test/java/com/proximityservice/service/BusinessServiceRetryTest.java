package com.proximityservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class BusinessServiceRetryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("proximity");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @MockitoBean
    private BusinessGeoRepository geoRepository;

    @MockitoBean
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessService businessService;

    @Test
    void syncGeoAdd_shouldRetryOnConnectionFailure() {
        doThrow(new RedisConnectionFailureException("Connection refused"))
                .doThrow(new RedisConnectionFailureException("Connection refused"))
                .doNothing()
                .when(geoRepository).add(anyString(), anyDouble(), anyDouble());

        businessService.syncGeoAdd("biz-1", 127.0, 37.5);

        verify(geoRepository, times(3)).add("biz-1", 127.0, 37.5);
    }

    @Test
    void syncGeoAdd_shouldRecoverAfterMaxRetries() {
        doThrow(new RedisConnectionFailureException("Connection refused"))
                .when(geoRepository).add(anyString(), anyDouble(), anyDouble());

        businessService.syncGeoAdd("biz-1", 127.0, 37.5);

        verify(geoRepository, times(3)).add("biz-1", 127.0, 37.5);
    }

    @Test
    void syncGeoRemove_shouldRetryOnConnectionFailure() {
        doThrow(new RedisConnectionFailureException("Connection refused"))
                .doNothing()
                .when(geoRepository).remove(anyString());

        businessService.syncGeoRemove("biz-1");

        verify(geoRepository, times(2)).remove("biz-1");
    }
}
