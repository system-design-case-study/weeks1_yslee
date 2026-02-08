package com.proximityservice.batch;

import com.proximityservice.domain.Business;
import com.proximityservice.dto.SyncBatchResult;
import com.proximityservice.repository.BusinessGeoRepository;
import com.proximityservice.repository.BusinessRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncBatchService {

    private static final int CHUNK_SIZE = 500;

    private final BusinessRepository businessRepository;
    private final BusinessGeoRepository geoRepository;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public SyncBatchResult fullSync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A batch job is already running");
        }

        LocalDateTime startedAt = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        int totalProcessed = 0;
        int added = 0;
        int errors = 0;

        try {
            geoRepository.deleteAll();

            int page = 0;
            Page<Business> chunk;
            do {
                chunk = businessRepository.findAll(PageRequest.of(page, CHUNK_SIZE));
                for (Business business : chunk.getContent()) {
                    try {
                        geoRepository.add(business.getId(), business.getLongitude(), business.getLatitude());
                        added++;
                    } catch (Exception e) {
                        errors++;
                        log.error("Failed to sync business {} to Redis: {}", business.getId(), e.getMessage());
                    }
                    totalProcessed++;
                }
                page++;
            } while (chunk.hasNext());

            String status = errors > 0 ? "PARTIAL_FAILURE" : "SUCCESS";
            long durationMs = System.currentTimeMillis() - startMs;

            SyncBatchResult result = new SyncBatchResult(
                    "FULL_SYNC", status, totalProcessed, added, 0, errors,
                    startedAt, LocalDateTime.now(), durationMs);

            log.info("Full sync completed: {}", result);
            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("Full sync failed: {}", e.getMessage(), e);
            return new SyncBatchResult(
                    "FULL_SYNC", "FAILED", totalProcessed, added, 0, errors + 1,
                    startedAt, LocalDateTime.now(), durationMs);
        } finally {
            running.set(false);
        }
    }

    public SyncBatchResult consistencyCheck() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A batch job is already running");
        }

        LocalDateTime startedAt = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        int added = 0;
        int removed = 0;
        int errors = 0;

        try {
            Set<String> mysqlIds = new HashSet<>();
            int page = 0;
            Page<Business> chunk;
            do {
                chunk = businessRepository.findAll(PageRequest.of(page, CHUNK_SIZE));
                for (Business b : chunk.getContent()) {
                    mysqlIds.add(b.getId());
                }
                page++;
            } while (chunk.hasNext());

            Set<String> redisMembers = geoRepository.getAllMembers();

            Set<String> missing = new HashSet<>(mysqlIds);
            missing.removeAll(redisMembers);

            Set<String> orphaned = new HashSet<>(redisMembers);
            orphaned.removeAll(mysqlIds);

            for (String id : missing) {
                try {
                    Business business = businessRepository.findById(id).orElse(null);
                    if (business != null) {
                        geoRepository.add(business.getId(), business.getLongitude(), business.getLatitude());
                        added++;
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("Failed to add missing business {} to Redis: {}", id, e.getMessage());
                }
            }

            for (String id : orphaned) {
                try {
                    geoRepository.remove(id);
                    removed++;
                } catch (Exception e) {
                    errors++;
                    log.error("Failed to remove orphaned business {} from Redis: {}", id, e.getMessage());
                }
            }

            int totalProcessed = mysqlIds.size() + orphaned.size();
            String status = errors > 0 ? "PARTIAL_FAILURE" : "SUCCESS";
            long durationMs = System.currentTimeMillis() - startMs;

            SyncBatchResult result = new SyncBatchResult(
                    "CONSISTENCY_CHECK", status, totalProcessed, added, removed, errors,
                    startedAt, LocalDateTime.now(), durationMs);

            log.info("Consistency check completed: {}", result);
            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("Consistency check failed: {}", e.getMessage(), e);
            return new SyncBatchResult(
                    "CONSISTENCY_CHECK", "FAILED", 0, added, removed, errors + 1,
                    startedAt, LocalDateTime.now(), durationMs);
        } finally {
            running.set(false);
        }
    }
}
