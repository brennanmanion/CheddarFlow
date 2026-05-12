package com.cheddarflow.collector.ingestion.api;

import java.time.Instant;
import java.util.UUID;

public record IngestionAcceptedResponse(
        String status,
        UUID requestId,
        Instant acceptedAtUtc,
        String detail
) {
    public static IngestionAcceptedResponse accepted(String detail) {
        return new IngestionAcceptedResponse(
                "ACCEPTED",
                UUID.randomUUID(),
                Instant.now(),
                detail
        );
    }
}
