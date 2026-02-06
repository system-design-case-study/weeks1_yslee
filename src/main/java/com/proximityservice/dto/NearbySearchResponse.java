package com.proximityservice.dto;

import java.util.List;

public record NearbySearchResponse(
        int total,
        List<BusinessSearchResult> businesses,
        String message
) {
    public NearbySearchResponse(int total, List<BusinessSearchResult> businesses) {
        this(total, businesses, null);
    }
}
