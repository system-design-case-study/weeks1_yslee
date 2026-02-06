package com.proximityservice.service;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.BusinessSeedRequest;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessSeedService {

    private final BusinessRepository businessRepository;
    private final BusinessGeoRepository geoRepository;

    @Transactional
    public int seed(List<BusinessSeedRequest> requests) {
        int count = 0;
        for (BusinessSeedRequest req : requests) {
            Business business = new Business(
                    req.name(),
                    req.address(),
                    req.latitude(),
                    req.longitude(),
                    req.category(),
                    req.phone(),
                    req.hours()
            );
            businessRepository.save(business);
            geoRepository.add(business.getId(), business.getLongitude(), business.getLatitude());
            count++;
        }
        return count;
    }
}
