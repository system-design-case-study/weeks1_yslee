package com.proximityservice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proximityservice.domain.Business;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SyncBatchControllerTest {

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
    }

    @Nested
    class FullSync {

        @Test
        void shouldRebuildRedisFromMysql() throws Exception {
            Business b1 = new Business("식당A", "서울시 강남구", 37.5012, 127.0396, "cafe", null, null);
            Business b2 = new Business("식당B", "서울시 서초구", 37.5050, 127.0350, "bar", null, null);
            businessRepository.save(b1);
            businessRepository.save(b2);

            mockMvc.perform(post("/v1/admin/sync/full"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("FULL_SYNC"))
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.total_processed").value(2))
                    .andExpect(jsonPath("$.added").value(2))
                    .andExpect(jsonPath("$.errors").value(0));

            mockMvc.perform(get("/v1/search/nearby")
                            .param("latitude", "37.503")
                            .param("longitude", "127.037")
                            .param("radius", "5000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2));
        }

        @Test
        void shouldReplaceExistingRedisData() throws Exception {
            Business b1 = new Business("식당A", "서울시 강남구", 37.5012, 127.0396, "cafe", null, null);
            businessRepository.save(b1);
            geoRepository.add("stale-id", 127.0396, 37.5012);
            geoRepository.add(b1.getId(), 127.0396, 37.5012);

            mockMvc.perform(post("/v1/admin/sync/full"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.added").value(1));

            mockMvc.perform(get("/v1/search/nearby")
                            .param("latitude", "37.5012")
                            .param("longitude", "127.0396")
                            .param("radius", "1000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.businesses[0].id").value(b1.getId()));
        }

        @Test
        void shouldHandleEmptyDatabase() throws Exception {
            mockMvc.perform(post("/v1/admin/sync/full"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.total_processed").value(0));
        }
    }

    @Nested
    class ConsistencyCheck {

        @Test
        void shouldAddMissingBusinesses() throws Exception {
            Business b1 = new Business("식당A", "서울시 강남구", 37.5, 127.0, "cafe", null, null);
            Business b2 = new Business("식당B", "서울시 서초구", 38.0, 128.0, "bar", null, null);
            businessRepository.save(b1);
            businessRepository.save(b2);
            geoRepository.add(b1.getId(), 127.0, 37.5);

            mockMvc.perform(post("/v1/admin/sync/consistency-check"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("CONSISTENCY_CHECK"))
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.added").value(1))
                    .andExpect(jsonPath("$.removed").value(0));
        }

        @Test
        void shouldRemoveOrphanedEntries() throws Exception {
            Business b1 = new Business("식당A", "서울시 강남구", 37.5, 127.0, "cafe", null, null);
            businessRepository.save(b1);
            geoRepository.add(b1.getId(), 127.0, 37.5);
            geoRepository.add("orphan-id", 127.0, 37.5);

            mockMvc.perform(post("/v1/admin/sync/consistency-check"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.added").value(0))
                    .andExpect(jsonPath("$.removed").value(1));
        }

        @Test
        void shouldReportNoChangesWhenConsistent() throws Exception {
            Business b1 = new Business("식당A", "서울시 강남구", 37.5, 127.0, "cafe", null, null);
            businessRepository.save(b1);
            geoRepository.add(b1.getId(), 127.0, 37.5);

            mockMvc.perform(post("/v1/admin/sync/consistency-check"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.added").value(0))
                    .andExpect(jsonPath("$.removed").value(0));
        }
    }
}
