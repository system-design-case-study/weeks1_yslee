package com.proximityservice.dto;

public record BusinessSeedRequest(
        String name,
        String address,
        Double latitude,
        Double longitude,
        String category,
        String phone,
        String hours
) {
}
