package com.proximityservice.service;

import com.proximityservice.domain.Business;
import com.proximityservice.domain.Category;
import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.BusinessUpdateRequest;
import com.proximityservice.exception.BusinessNotFoundException;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
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
        geoRepository.add(business.getId(), business.getLongitude(), business.getLatitude());
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
            geoRepository.remove(business.getId());
            geoRepository.add(business.getId(), business.getLongitude(), business.getLatitude());
        }

        return business;
    }

    @Transactional
    public void delete(String id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new BusinessNotFoundException(id));

        businessRepository.delete(business);
        geoRepository.remove(business.getId());
    }
}
