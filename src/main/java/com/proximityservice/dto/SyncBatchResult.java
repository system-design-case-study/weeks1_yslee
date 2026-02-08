package com.proximityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record SyncBatchResult(
        String type,
        String status,
        @JsonProperty("total_processed") int totalProcessed,
        int added,
        int removed,
        int errors,
        @JsonProperty("started_at") LocalDateTime startedAt,
        @JsonProperty("finished_at") LocalDateTime finishedAt,
        @JsonProperty("duration_ms") long durationMs
) {
}
