package com.proximityservice.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.SyncBatchResult;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SyncBatchServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessGeoRepository geoRepository;

    @InjectMocks
    private SyncBatchService syncBatchService;

    @Test
    void fullSync_shouldRebuildRedisFromMysql() {
        Business b1 = new Business("식당A", "주소A", 37.5, 127.0, "cafe", null, null);
        Business b2 = new Business("식당B", "주소B", 38.0, 128.0, "bar", null, null);
        PageImpl<Business> page = new PageImpl<>(List.of(b1, b2), PageRequest.of(0, 500), 2);

        given(businessRepository.findAll(any(Pageable.class))).willReturn(page);

        SyncBatchResult result = syncBatchService.fullSync();

        assertThat(result.type()).isEqualTo("FULL_SYNC");
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.totalProcessed()).isEqualTo(2);
        assertThat(result.added()).isEqualTo(2);
        assertThat(result.errors()).isZero();
        then(geoRepository).should().deleteAll();
        then(geoRepository).should().add(b1.getId(), 127.0, 37.5);
        then(geoRepository).should().add(b2.getId(), 128.0, 38.0);
    }

    @Test
    void fullSync_shouldReturnZeroWhenDbEmpty() {
        PageImpl<Business> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 500), 0);
        given(businessRepository.findAll(any(Pageable.class))).willReturn(emptyPage);

        SyncBatchResult result = syncBatchService.fullSync();

        assertThat(result.type()).isEqualTo("FULL_SYNC");
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.totalProcessed()).isZero();
        assertThat(result.added()).isZero();
    }

    @Test
    void fullSync_shouldReportPartialFailureOnRedisError() {
        Business b1 = new Business("식당A", "주소A", 37.5, 127.0, "cafe", null, null);
        PageImpl<Business> page = new PageImpl<>(List.of(b1), PageRequest.of(0, 500), 1);
        given(businessRepository.findAll(any(Pageable.class))).willReturn(page);
        doThrow(new RuntimeException("Redis error"))
                .when(geoRepository).add(anyString(), anyDouble(), anyDouble());

        SyncBatchResult result = syncBatchService.fullSync();

        assertThat(result.status()).isEqualTo("PARTIAL_FAILURE");
        assertThat(result.errors()).isEqualTo(1);
    }

    @Test
    void consistencyCheck_shouldAddMissingAndRemoveOrphaned() {
        Business b1 = new Business("식당A", "주소A", 37.5, 127.0, "cafe", null, null);
        Business b2 = new Business("식당B", "주소B", 38.0, 128.0, "bar", null, null);
        PageImpl<Business> page = new PageImpl<>(List.of(b1, b2), PageRequest.of(0, 500), 2);

        given(businessRepository.findAll(any(Pageable.class))).willReturn(page);
        given(geoRepository.getAllMembers()).willReturn(Set.of(b1.getId(), "orphan-id"));
        given(businessRepository.findById(b2.getId())).willReturn(Optional.of(b2));

        SyncBatchResult result = syncBatchService.consistencyCheck();

        assertThat(result.type()).isEqualTo("CONSISTENCY_CHECK");
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.added()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        then(geoRepository).should().add(b2.getId(), 128.0, 38.0);
        then(geoRepository).should().remove("orphan-id");
    }

    @Test
    void consistencyCheck_shouldReportNoChangesWhenConsistent() {
        Business b1 = new Business("식당A", "주소A", 37.5, 127.0, "cafe", null, null);
        PageImpl<Business> page = new PageImpl<>(List.of(b1), PageRequest.of(0, 500), 1);

        given(businessRepository.findAll(any(Pageable.class))).willReturn(page);
        given(geoRepository.getAllMembers()).willReturn(Set.of(b1.getId()));

        SyncBatchResult result = syncBatchService.consistencyCheck();

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.added()).isZero();
        assertThat(result.removed()).isZero();
    }
}
