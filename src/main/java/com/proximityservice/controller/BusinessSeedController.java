package com.proximityservice.controller;

import com.proximityservice.dto.BusinessSeedRequest;
import com.proximityservice.service.BusinessSeedService;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BusinessSeedController {

    private final BusinessSeedService seedService;

    @PostMapping("/v1/businesses/seed")
    public ResponseEntity<Map<String, Integer>> seed(@RequestBody List<BusinessSeedRequest> requests) {
        int createdCount = seedService.seed(requests);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("created_count", createdCount));
    }
}
