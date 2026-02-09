package com.proximityservice.controller;

import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.exception.InvalidParameterException;
import com.proximityservice.service.NearbySearchService;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NearbySearchController {

    private final NearbySearchService searchService;

    @GetMapping("/v1/search/nearby")
    public ResponseEntity<NearbySearchResponse> searchNearby(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5000") int radius,
            @RequestParam(defaultValue = "20") int limit) {

        validateParameters(latitude, longitude, radius, limit);

        NearbySearchResponse response = searchService.searchNearby(latitude, longitude, radius, limit);
        return ResponseEntity.ok(response);
    }

    private void validateParameters(double latitude, double longitude, int radius, int limit) {
        if (limit < 1 || limit > 50) {
            throw new InvalidParameterException(
                    "결과 수 제한은 1에서 50 사이여야 합니다.",
                    Map.of("field", "limit", "valid_range", "1 ~ 50", "received", limit)
            );
        }
        if (latitude < -90 || latitude > 90) {
            throw new InvalidParameterException(
                    "위도는 -90에서 90 사이여야 합니다.",
                    Map.of("field", "latitude", "valid_range", "-90 ~ 90", "received", latitude)
            );
        }
        if (longitude < -180 || longitude > 180) {
            throw new InvalidParameterException(
                    "경도는 -180에서 180 사이여야 합니다.",
                    Map.of("field", "longitude", "valid_range", "-180 ~ 180", "received", longitude)
            );
        }
        if (radius < 1 || radius > 20000) {
            throw new InvalidParameterException(
                    "검색 반경은 1에서 20000m 사이여야 합니다.",
                    Map.of("field", "radius", "valid_range", "1 ~ 20000", "received", radius)
            );
        }
    }
}
