package com.cheddarflow.market.enrichment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketEnrichmentRecord(
        UUID id,
        UUID sourceSessionId,
        UUID normalizedEventId,
        String symbol,
        String expiry,
        String strike,
        String putCall,
        BigDecimal premiumNumeric,
        String enrichmentStatus,
        String ohlcStatus,
        String openInterestStatus,
        String gammaWallsStatus,
        String level2Status,
        String providerNotes,
        String idempotencyKey,
        Instant createdAtUtc,
        Instant updatedAtUtc
) {
}
