package com.proximityservice.scenario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.BusinessUpdateRequest;
import com.proximityservice.support.BaseIntegrationTest;
import com.proximityservice.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("E2E: 사업장 생애주기 전체 흐름")
class BusinessLifecycleScenarioTest extends BaseIntegrationTest {

    @Test
    @DisplayName("등록 → 검색 → 상세조회 → 수정(좌표이동) → 검색 → 삭제 → 404")
    void fullBusinessLifecycle() throws Exception {
        printHeader("E2E: 사업장 생애주기 시나리오");

        // Step 1: 5개 업체 시딩 (HTTP API)
        String[] ids = new String[5];
        BusinessCreateRequest[] seeds = {
                TestDataFactory.gangnamBusiness("강남 식당"),
                TestDataFactory.hongdaeBusiness("홍대 카페"),
                TestDataFactory.jamsilBusiness("잠실 바"),
                TestDataFactory.seoulStationBusiness("서울역 편의점"),
                TestDataFactory.myeongdongBusiness("명동 약국")
        };

        for (int i = 0; i < seeds.length; i++) {
            MvcResult result = mockMvc.perform(post("/v1/businesses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(seeds[i])))
                    .andExpect(status().isCreated())
                    .andReturn();
            ids[i] = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        }
        printResult("Step 1: 5개 시딩", "완료");

        // Step 2: 강남 근처 검색
        mockMvc.perform(get("/v1/search/nearby")
                        .param("latitude", String.valueOf(TestDataFactory.GANGNAM_LAT))
                        .param("longitude", String.valueOf(TestDataFactory.GANGNAM_LNG))
                        .param("radius", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.businesses[0].name").value("강남 식당"));
        printResult("Step 2: 강남 검색", "1건 확인");

        // Step 3: 상세조회
        mockMvc.perform(get("/v1/businesses/{id}", ids[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("강남 식당"))
                .andExpect(jsonPath("$.category").value("korean_food"));
        printResult("Step 3: 상세조회", "강남 식당 확인");

        // Step 4: 좌표 이동 (강남 → 홍대 근처)
        BusinessUpdateRequest moveRequest = new BusinessUpdateRequest(
                "이전한 식당", "서울시 마포구",
                TestDataFactory.HONGDAE_LAT + 0.001, TestDataFactory.HONGDAE_LNG,
                "korean_food", null, null);

        mockMvc.perform(put("/v1/businesses/{id}", ids[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이전한 식당"));
        printResult("Step 4: 좌표 이동", "강남 → 홍대 근처");

        // Step 5: 새 위치(홍대)에서 검색
        mockMvc.perform(get("/v1/search/nearby")
                        .param("latitude", String.valueOf(TestDataFactory.HONGDAE_LAT))
                        .param("longitude", String.valueOf(TestDataFactory.HONGDAE_LNG))
                        .param("radius", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businesses[?(@.name == '이전한 식당')]").exists())
                .andExpect(jsonPath("$.businesses[?(@.name == '홍대 카페')]").exists());
        printResult("Step 5: 홍대 검색", "이전한 식당 + 홍대 카페 확인");

        // Step 6: 구 위치(강남)에서 검색 → 없음
        mockMvc.perform(get("/v1/search/nearby")
                        .param("latitude", String.valueOf(TestDataFactory.GANGNAM_LAT))
                        .param("longitude", String.valueOf(TestDataFactory.GANGNAM_LNG))
                        .param("radius", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        printResult("Step 6: 강남 재검색", "0건 (이전 완료)");

        // Step 7: 삭제
        mockMvc.perform(delete("/v1/businesses/{id}", ids[0]))
                .andExpect(status().isNoContent());
        printResult("Step 7: 삭제", ids[0]);

        // Step 8: 삭제된 업체 404
        mockMvc.perform(get("/v1/businesses/{id}", ids[0]))
                .andExpect(status().isNotFound());
        printResult("Step 8: 삭제 확인", "404 반환");

        // Step 9: 나머지 4개 건재 확인
        for (int i = 1; i < 5; i++) {
            mockMvc.perform(get("/v1/businesses/{id}", ids[i]))
                    .andExpect(status().isOk());
        }
        printResult("Step 9: 나머지 4개", "건재 확인");
        printPass("전체 생애주기 시나리오");
    }
}
