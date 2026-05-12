package com.cheddarflow.shared.domain.collector;

import java.time.Instant;
import java.util.UUID;

public record CollectorHeartbeat(
        UUID sessionId,
        String collectorType,
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
        boolean stale,
        String staleReason,
        String sourceSelector,
        String reason,
        Instant heartbeatAtUtc
) {
}
