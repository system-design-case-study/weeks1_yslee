package com.proximityservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.BusinessCreateRequest;
import com.proximityservice.dto.BusinessUpdateRequest;
import com.proximityservice.exception.BusinessNotFoundException;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessGeoRepository geoRepository;

    @InjectMocks
    private BusinessService businessService;

    @Test
    void create_shouldSaveToDbAndRedis() {
        var request = new BusinessCreateRequest(
                "맛있는 식당", "서울시 강남구", 37.5012, 127.0396,
                "korean_food", "02-1234-5678", "09:00-22:00");

        Business result = businessService.create(request);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("맛있는 식당");
        assertThat(result.getCategory()).isEqualTo("korean_food");
        then(businessRepository).should().save(any(Business.class));
        then(geoRepository).should().add(result.getId(), 127.0396, 37.5012);
    }

    @Test
    void create_shouldRejectInvalidCategory() {
        var request = new BusinessCreateRequest(
                "식당", "주소", 37.5, 127.0, "invalid_category", null, null);

        assertThatThrownBy(() -> businessService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid category");
    }

    @Test
    void getById_shouldReturnBusiness() {
        Business business = new Business("식당", "주소", 37.5, 127.0, "cafe", null, null);
        given(businessRepository.findById(business.getId())).willReturn(Optional.of(business));

        Business result = businessService.getById(business.getId());

        assertThat(result.getName()).isEqualTo("식당");
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        given(businessRepository.findById("nonexistent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> businessService.getById("nonexistent"))
                .isInstanceOf(BusinessNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void update_shouldNotSyncRedisWhenCoordinatesUnchanged() {
        Business business = new Business("식당", "주소", 37.5, 127.0, "cafe", null, null);
        given(businessRepository.findById(business.getId())).willReturn(Optional.of(business));

        var request = new BusinessUpdateRequest(
                "새 이름", "새 주소", 37.5, 127.0, "cafe", "02-1111-2222", "10:00-20:00");

        Business result = businessService.update(business.getId(), request);

        assertThat(result.getName()).isEqualTo("새 이름");
        assertThat(result.getPhone()).isEqualTo("02-1111-2222");
        then(geoRepository).should(never()).remove(any());
        then(geoRepository).should(never()).add(any(), any(double.class), any(double.class));
    }

    @Test
    void update_shouldSyncRedisWhenCoordinatesChanged() {
        Business business = new Business("식당", "주소", 37.5, 127.0, "cafe", null, null);
        given(businessRepository.findById(business.getId())).willReturn(Optional.of(business));

        var request = new BusinessUpdateRequest(
                "식당", "주소", 38.0, 128.0, "cafe", null, null);

        businessService.update(business.getId(), request);

        then(geoRepository).should().remove(business.getId());
        then(geoRepository).should().add(business.getId(), 128.0, 38.0);
    }

    @Test
    void update_shouldThrowWhenNotFound() {
        given(businessRepository.findById("nonexistent")).willReturn(Optional.empty());

        var request = new BusinessUpdateRequest("이름", "주소", 37.5, 127.0, "cafe", null, null);

        assertThatThrownBy(() -> businessService.update("nonexistent", request))
                .isInstanceOf(BusinessNotFoundException.class);
    }

    @Test
    void update_shouldRejectInvalidCategory() {
        var request = new BusinessUpdateRequest("이름", "주소", 37.5, 127.0, "bad", null, null);

        assertThatThrownBy(() -> businessService.update("any-id", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid category");
    }

    @Test
    void delete_shouldRemoveFromDbAndRedis() {
        Business business = new Business("식당", "주소", 37.5, 127.0, "cafe", null, null);
        given(businessRepository.findById(business.getId())).willReturn(Optional.of(business));

        businessService.delete(business.getId());

        then(businessRepository).should().delete(business);
        then(geoRepository).should().remove(business.getId());
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        given(businessRepository.findById("nonexistent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> businessService.delete("nonexistent"))
                .isInstanceOf(BusinessNotFoundException.class);
    }
}
