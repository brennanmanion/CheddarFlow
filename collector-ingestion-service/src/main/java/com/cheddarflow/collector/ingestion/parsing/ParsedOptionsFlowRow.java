package com.cheddarflow.collector.ingestion.parsing;

import java.math.BigDecimal;

public record ParsedOptionsFlowRow(
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
        String conditions
) {
}
