package com.proximityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.proximityservice.domain.Business;
import java.time.LocalDateTime;

public record BusinessDetailResponse(
        String id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String category,
        String phone,
        String hours,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
    public static BusinessDetailResponse from(Business business) {
        return new BusinessDetailResponse(
                business.getId(),
                business.getName(),
                business.getAddress(),
                business.getLatitude(),
                business.getLongitude(),
                business.getCategory(),
                business.getPhone(),
                business.getHours(),
                business.getCreatedAt(),
                business.getUpdatedAt()
        );
    }
}
