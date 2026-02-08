package com.proximityservice.service;

import com.proximityservice.domain.Business;
import com.proximityservice.domain.Category;
import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.BusinessUpdateRequest;
import com.proximityservice.exception.BusinessNotFoundException;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final BusinessGeoRepository geoRepository;

    @Transactional
    public Business create(BusinessCreateRequest request) {
        Category.fromValue(request.category());

        Business business = new Business(
                request.name(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.category(),
                request.phone(),
                request.hours()
        );
        businessRepository.save(business);
        syncGeoAdd(business.getId(), business.getLongitude(), business.getLatitude());
        return business;
    }

    @Transactional(readOnly = true)
    public Business getById(String id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new BusinessNotFoundException(id));
    }

    @Transactional
    public Business update(String id, BusinessUpdateRequest request) {
        Category.fromValue(request.category());

        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new BusinessNotFoundException(id));

        boolean coordinatesChanged = business.update(
                request.name(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.category(),
                request.phone(),
                request.hours()
        );

        if (coordinatesChanged) {
            syncGeoRemove(business.getId());
            syncGeoAdd(business.getId(), business.getLongitude(), business.getLatitude());
        }

        return business;
    }

    @Transactional
    public void delete(String id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new BusinessNotFoundException(id));

        businessRepository.delete(business);
        syncGeoRemove(business.getId());
    }

    @Retryable(
            retryFor = {RedisConnectionFailureException.class, QueryTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void syncGeoAdd(String businessId, double longitude, double latitude) {
        geoRepository.add(businessId, longitude, latitude);
    }

    @Retryable(
            retryFor = {RedisConnectionFailureException.class, QueryTimeoutException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void syncGeoRemove(String businessId) {
        geoRepository.remove(businessId);
    }

    @Recover
    public void recoverGeoAdd(RedisConnectionFailureException ex, String businessId, double longitude, double latitude) {
        log.error("Redis sync failed after 3 retries for GEOADD business={}, lng={}, lat={}: {}",
                businessId, longitude, latitude, ex.getMessage());
    }

    @Recover
    public void recoverGeoRemove(RedisConnectionFailureException ex, String businessId) {
        log.error("Redis sync failed after 3 retries for GEOREMOVE business={}: {}",
                businessId, ex.getMessage());
    }

    @Recover
    public void recoverGeoAddTimeout(QueryTimeoutException ex, String businessId, double longitude, double latitude) {
        log.error("Redis sync timed out after 3 retries for GEOADD business={}, lng={}, lat={}: {}",
                businessId, longitude, latitude, ex.getMessage());
    }

    @Recover
    public void recoverGeoRemoveTimeout(QueryTimeoutException ex, String businessId) {
        log.error("Redis sync timed out after 3 retries for GEOREMOVE business={}: {}",
                businessId, ex.getMessage());
    }
}
