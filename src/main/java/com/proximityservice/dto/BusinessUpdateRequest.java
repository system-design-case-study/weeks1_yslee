package com.proximityservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BusinessUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotBlank String category,
        @Size(max = 20) String phone,
        @Size(max = 100) String hours
) {
}
