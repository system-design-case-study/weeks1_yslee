package com.proximityservice.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proximityservice.batch.SyncBatchService;
import com.proximityservice.domain.Business;
import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import com.proximityservice.service.BusinessService;
import com.proximityservice.service.NearbySearchService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    private static final String GEO_KEY = "geo:businesses";

    // Singleton containers - started once, reused across all test classes
    static final MySQLContainer<?> mysql;
    static final GenericContainer<?> redis;

    static {
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("proximity");
        mysql.start();

        redis = new GenericContainer<>("redis:7")
                .withExposedPorts(6379);
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected BusinessRepository businessRepository;

    @Autowired
    protected BusinessGeoRepository geoRepository;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected BusinessService businessService;

    @Autowired
    protected NearbySearchService nearbySearchService;

    @Autowired
    protected SyncBatchService syncBatchService;

    @BeforeEach
    void cleanUp() {
        businessRepository.deleteAll();
        redisTemplate.delete(GEO_KEY);
    }

    // -- Helper methods --

    protected Business createAndPersist(BusinessCreateRequest request) {
        return businessService.create(request);
    }

    protected Set<String> getRedisMembers() {
        return geoRepository.getAllMembers();
    }

    protected long getRedisMemberCount() {
        Long size = redisTemplate.opsForZSet().zCard(GEO_KEY);
        return size != null ? size : 0;
    }

    // -- Print helpers --

    protected static void printHeader(String title) {
        System.out.println();
        System.out.println("============================================================");
        System.out.printf("  %s%n", title);
        System.out.println("============================================================");
    }

    protected static void printResult(String label, Object value) {
        System.out.printf("  %-30s : %s%n", label, value);
    }

    protected static void printPass(String label) {
        System.out.printf("  %-30s : PASS%n", label);
    }

    protected static void printPassFail(String label, boolean pass) {
        System.out.printf("  %-30s : %s%n", label, pass ? "PASS" : "FAIL");
    }
}
