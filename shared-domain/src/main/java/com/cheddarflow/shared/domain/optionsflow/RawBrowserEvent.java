package com.cheddarflow.shared.domain.optionsflow;

import java.time.Instant;
import java.util.UUID;

public record RawBrowserEvent(
        UUID id,
        UUID sessionId,
        String collectorType,
        String pageUrl,
        String pageTitle,
        String sourceSelector,
        String domKey,
        String sourceHtml,
        String sourceText,
        String rowSignature,
        String sourceHash,
        String clientHash,
        String observedVia,
        Instant capturedAtUtc,
        Instant ingestedAtUtc
) {
}
