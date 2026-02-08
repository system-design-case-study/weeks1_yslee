package com.proximityservice.controller;

import com.proximityservice.batch.SyncBatchService;
import com.proximityservice.dto.SyncBatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/sync")
@RequiredArgsConstructor
public class SyncBatchController {

    private final SyncBatchService syncBatchService;

    @PostMapping("/full")
    public ResponseEntity<SyncBatchResult> fullSync() {
        SyncBatchResult result = syncBatchService.fullSync();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/consistency-check")
    public ResponseEntity<SyncBatchResult> consistencyCheck() {
        SyncBatchResult result = syncBatchService.consistencyCheck();
        return ResponseEntity.ok(result);
    }
}
