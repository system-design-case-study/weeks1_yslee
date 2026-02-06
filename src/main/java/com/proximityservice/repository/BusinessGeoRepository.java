package com.proximityservice.repository;

import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessGeoRepository {

    private static final String GEO_KEY = "geo:businesses";

    private final GeoOperations<String, String> geoOps;

    public BusinessGeoRepository(StringRedisTemplate redisTemplate) {
        this.geoOps = redisTemplate.opsForGeo();
    }

    public void add(String businessId, double longitude, double latitude) {
        geoOps.add(GEO_KEY, new Point(longitude, latitude), businessId);
    }

    public GeoResults<GeoLocation<String>> searchNearby(double longitude, double latitude,
                                                         double radiusMeters) {
        return geoOps.search(
                GEO_KEY,
                GeoReference.fromCoordinate(longitude, latitude),
                new Distance(radiusMeters, Metrics.METERS),
                GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending()
        );
    }

    public void remove(String businessId) {
        geoOps.remove(GEO_KEY, businessId);
    }
}
