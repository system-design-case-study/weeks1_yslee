package com.proximityservice.controller;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.BusinessDetailResponse;
import com.proximityservice.dto.BusinessUpdateRequest;
import com.proximityservice.service.BusinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @GetMapping("/{id}")
    public ResponseEntity<BusinessDetailResponse> getBusiness(@PathVariable String id) {
        Business business = businessService.getById(id);
        return ResponseEntity.ok(BusinessDetailResponse.from(business));
    }

    @PostMapping
    public ResponseEntity<BusinessDetailResponse> createBusiness(
            @RequestBody @Valid BusinessCreateRequest request) {
        Business business = businessService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BusinessDetailResponse.from(business));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessDetailResponse> updateBusiness(
            @PathVariable String id,
            @RequestBody @Valid BusinessUpdateRequest request) {
        Business business = businessService.update(id, request);
        return ResponseEntity.ok(BusinessDetailResponse.from(business));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBusiness(@PathVariable String id) {
        businessService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
