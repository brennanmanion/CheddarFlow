package com.cheddarflow.shared.domain.optionsflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NormalizedOptionsFlowEvent(
        UUID id,
        UUID rawEventId,
        UUID sessionId,
        String eventTimeText,
        String eventDateText,
        String symbol,
        String expiry,
        String strike,
        String putCall,
        String side,
        String buySell,
        String spot,
        String size,
        String price,
        String premiumText,
        BigDecimal premiumNumeric,
        String sweepBlockSplit,
        String volume,
        String openInterest,
        String conditions,
        Instant capturedAtUtc
) {
}
