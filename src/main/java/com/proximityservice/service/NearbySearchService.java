package com.proximityservice.service;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.BusinessSearchResult;
import com.proximityservice.dto.NearbySearchResponse;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NearbySearchService {

    private final BusinessGeoRepository geoRepository;
    private final BusinessRepository businessRepository;

    public NearbySearchResponse searchNearby(double latitude, double longitude, int radius) {
        GeoResults<GeoLocation<String>> geoResults =
                geoRepository.searchNearby(longitude, latitude, radius);

        if (geoResults == null || geoResults.getContent().isEmpty()) {
            return new NearbySearchResponse(0, List.of(),
                    "검색 결과가 없습니다. 반경을 넓혀서 다시 검색해 보세요.");
        }

        List<String> businessIds = geoResults.getContent().stream()
                .map(result -> result.getContent().getName())
                .toList();

        Map<String, Business> businessMap = businessRepository.findAllByIdIn(businessIds)
                .stream()
                .collect(Collectors.toMap(Business::getId, Function.identity()));

        List<BusinessSearchResult> results = new ArrayList<>();
        for (GeoResult<GeoLocation<String>> geoResult : geoResults.getContent()) {
            String id = geoResult.getContent().getName();
            Business business = businessMap.get(id);
            if (business != null) {
                double distanceM = geoResult.getDistance().in(org.springframework.data.redis.domain.geo.Metrics.METERS).getValue();
                results.add(new BusinessSearchResult(
                        business.getId(),
                        business.getName(),
                        business.getAddress(),
                        business.getLatitude(),
                        business.getLongitude(),
                        Math.round(distanceM * 10.0) / 10.0,
                        business.getCategory()
                ));
            }
        }

        return new NearbySearchResponse(results.size(), results);
    }
}
