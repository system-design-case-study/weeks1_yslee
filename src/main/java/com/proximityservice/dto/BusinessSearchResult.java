package com.proximityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BusinessSearchResult(
        String id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        @JsonProperty("distance_m") Double distanceM,
        String category
) {
}
