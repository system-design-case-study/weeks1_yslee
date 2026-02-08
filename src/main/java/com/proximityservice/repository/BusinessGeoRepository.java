package com.proximityservice.repository;

import java.util.Collections;
import java.util.Set;
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
    private final StringRedisTemplate redisTemplate;

    public BusinessGeoRepository(StringRedisTemplate redisTemplate) {
        this.geoOps = redisTemplate.opsForGeo();
        this.redisTemplate = redisTemplate;
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

    public Set<String> getAllMembers() {
        Set<String> members = redisTemplate.opsForZSet().range(GEO_KEY, 0, -1);
        return members != null ? members : Collections.emptySet();
    }

    public void deleteAll() {
        redisTemplate.delete(GEO_KEY);
    }
}
