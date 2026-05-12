package com.cheddarflow.collector.ingestion.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CollectorHeartbeatRequest(
        @NotBlank String collectorType,
        @NotNull UUID sessionId,
        String pageUrl,
        String pageTitle,
        boolean attached,
        int attachAttempts,
        int queuedEventCount,
        int capturedEventCount,
        int duplicateCount,
        int parseFailureCount,
        int sendFailureCount,
        Instant lastCaptureAtUtc,
        Double captureAgeSeconds,
        @JsonAlias("is_stale") boolean stale,
        String staleReason,
        String sourceSelector,
        String reason,
        @NotNull Instant heartbeatAtUtc
) {
}
