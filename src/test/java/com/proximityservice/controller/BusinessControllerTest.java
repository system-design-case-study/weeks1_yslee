package com.proximityservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proximityservice.domain.Business;
import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.BusinessUpdateRequest;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BusinessControllerTest {

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
    private ObjectMapper objectMapper;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessGeoRepository geoRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Business savedBusiness;

    @BeforeEach
    void setUp() {
        businessRepository.deleteAll();
        redisTemplate.delete("geo:businesses");

        savedBusiness = new Business("맛있는 식당", "서울시 강남구 테헤란로 123",
                37.5012, 127.0396, "korean_food", "02-1234-5678", "11:00-22:00");
        businessRepository.save(savedBusiness);
        geoRepository.add(savedBusiness.getId(), savedBusiness.getLongitude(), savedBusiness.getLatitude());
    }

    @Nested
    class GetBusiness {

        @Test
        void shouldReturnBusinessDetail() throws Exception {
            mockMvc.perform(get("/v1/businesses/{id}", savedBusiness.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(savedBusiness.getId()))
                    .andExpect(jsonPath("$.name").value("맛있는 식당"))
                    .andExpect(jsonPath("$.address").value("서울시 강남구 테헤란로 123"))
                    .andExpect(jsonPath("$.latitude").value(37.5012))
                    .andExpect(jsonPath("$.longitude").value(127.0396))
                    .andExpect(jsonPath("$.category").value("korean_food"))
                    .andExpect(jsonPath("$.phone").value("02-1234-5678"))
                    .andExpect(jsonPath("$.hours").value("11:00-22:00"))
                    .andExpect(jsonPath("$.created_at").exists())
                    .andExpect(jsonPath("$.updated_at").exists());
        }

        @Test
        void shouldReturn404WhenNotFound() throws Exception {
            mockMvc.perform(get("/v1/businesses/{id}", "nonexistent-id"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }
    }

    @Nested
    class CreateBusiness {

        @Test
        void shouldCreateAndReturnBusiness() throws Exception {
            var request = new BusinessCreateRequest(
                    "새 카페", "서울시 서초구 서초대로 100",
                    37.4900, 127.0200, "cafe", "02-5555-6666", "08:00-23:00");

            mockMvc.perform(post("/v1/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.name").value("새 카페"))
                    .andExpect(jsonPath("$.category").value("cafe"));
        }

        @Test
        void shouldBeSearchableAfterCreate() throws Exception {
            var request = new BusinessCreateRequest(
                    "검색될 식당", "서울시 강남구", 37.5012, 127.0396,
                    "korean_food", null, null);

            MvcResult result = mockMvc.perform(post("/v1/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

            mockMvc.perform(get("/v1/search/nearby")
                            .param("latitude", "37.5012")
                            .param("longitude", "127.0396")
                            .param("radius", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.businesses[?(@.id == '%s')]", id).exists());
        }

        @Test
        void shouldReturn400WhenNameMissing() throws Exception {
            String json = """
                    {"latitude": 37.5, "longitude": 127.0, "category": "cafe"}
                    """;

            mockMvc.perform(post("/v1/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        void shouldReturn400WhenLatitudeOutOfRange() throws Exception {
            var request = new BusinessCreateRequest("식당", "주소", 91.0, 127.0, "cafe", null, null);

            mockMvc.perform(post("/v1/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400WhenInvalidCategory() throws Exception {
            var request = new BusinessCreateRequest("식당", "주소", 37.5, 127.0, "invalid", null, null);

            mockMvc.perform(post("/v1/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid category")));
        }

        @Test
        void shouldReturn400WhenNameExceedsMaxLength() throws Exception {
            String longName = "a".repeat(256);
            var request = new BusinessCreateRequest(longName, null, 37.5, 127.0, "cafe", null, null);

            mockMvc.perform(post("/v1/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class UpdateBusiness {

        @Test
        void shouldUpdateBusinessInfo() throws Exception {
            var request = new BusinessUpdateRequest(
                    "이름 변경", "새 주소", 37.5012, 127.0396,
                    "cafe", "02-9999-8888", "10:00-23:00");

            mockMvc.perform(put("/v1/businesses/{id}", savedBusiness.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("이름 변경"))
                    .andExpect(jsonPath("$.category").value("cafe"))
                    .andExpect(jsonPath("$.phone").value("02-9999-8888"));
        }

        @Test
        void shouldUpdateRedisWhenCoordinatesChanged() throws Exception {
            double newLat = 38.0;
            double newLng = 128.0;
            var request = new BusinessUpdateRequest(
                    "이전한 식당", "부산시", newLat, newLng,
                    "korean_food", null, null);

            mockMvc.perform(put("/v1/businesses/{id}", savedBusiness.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/v1/search/nearby")
                            .param("latitude", String.valueOf(newLat))
                            .param("longitude", String.valueOf(newLng))
                            .param("radius", "1000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.businesses[0].name").value("이전한 식당"));

            mockMvc.perform(get("/v1/search/nearby")
                            .param("latitude", "37.5012")
                            .param("longitude", "127.0396")
                            .param("radius", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
        }

        @Test
        void shouldReturn404WhenNotFound() throws Exception {
            var request = new BusinessUpdateRequest("이름", "주소", 37.5, 127.0, "cafe", null, null);

            mockMvc.perform(put("/v1/businesses/{id}", "nonexistent-id")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn400WhenInvalidCategory() throws Exception {
            var request = new BusinessUpdateRequest("이름", "주소", 37.5, 127.0, "bad_cat", null, null);

            mockMvc.perform(put("/v1/businesses/{id}", savedBusiness.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class DeleteBusiness {

        @Test
        void shouldDeleteBusiness() throws Exception {
            mockMvc.perform(delete("/v1/businesses/{id}", savedBusiness.getId()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/v1/businesses/{id}", savedBusiness.getId()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldRemoveFromSearchAfterDelete() throws Exception {
            mockMvc.perform(delete("/v1/businesses/{id}", savedBusiness.getId()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/v1/search/nearby")
                            .param("latitude", "37.5012")
                            .param("longitude", "127.0396")
                            .param("radius", "1000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
        }

        @Test
        void shouldReturn404WhenNotFound() throws Exception {
            mockMvc.perform(delete("/v1/businesses/{id}", "nonexistent-id"))
                    .andExpect(status().isNotFound());
        }
    }
}
