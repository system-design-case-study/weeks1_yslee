package com.proximityservice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proximityservice.domain.Business;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NearbySearchControllerTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("proximity");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

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
        businessRepository.save(b1);
        geoRepository.add(b1.getId(), b1.getLongitude(), b1.getLatitude());
    }

    @Test
    void shouldReturn200WithResults() throws Exception {
        mockMvc.perform(get("/v1/search/nearby")
                        .param("latitude", "37.5012")
                        .param("longitude", "127.0396")
                        .param("radius", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.businesses[0].name").value("맛있는 식당"))
                .andExpect(jsonPath("$.businesses[0].category").value("korean_food"));
    }

    @Test
    void shouldApplyDefaultRadius() throws Exception {
        mockMvc.perform(get("/v1/search/nearby")
                        .param("latitude", "37.5012")
                        .param("longitude", "127.0396"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void shouldReturnEmptyWithMessageWhenNoResults() throws Exception {
        mockMvc.perform(get("/v1/search/nearby")
                        .param("latitude", "36.0")
                        .param("longitude", "126.0")
                        .param("radius", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.businesses").isEmpty())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldReturn400ForInvalidLatitude() throws Exception {
        mockMvc.perform(get("/v1/search/nearby")
                        .param("latitude", "91")
                        .param("longitude", "127.0396"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details.field").value("latitude"));
    }

    @Test
    void shouldReturn400ForInvalidLongitude() throws Exception {
        mockMvc.perform(get("/v1/search/nearby")
                        .param("latitude", "37.5")
                        .param("longitude", "200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details.field").value("longitude"));
    }

    @Test
    void shouldReturn400ForRadiusExceeded() throws Exception {
        mockMvc.perform(get("/v1/search/nearby")
                        .param("latitude", "37.5")
                        .param("longitude", "127.0")
                        .param("radius", "50000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details.field").value("radius"));
    }
}
