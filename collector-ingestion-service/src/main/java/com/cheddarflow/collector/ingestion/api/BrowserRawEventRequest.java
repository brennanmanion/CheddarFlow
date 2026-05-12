package com.cheddarflow.collector.ingestion.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BrowserRawEventRequest(
        @NotBlank String collectorType,
        @NotNull UUID sessionId,
        String pageUrl,
        String pageTitle,
        String sourceSelector,
        String domKey,
        @NotBlank String sourceHtml,
        String sourceText,
        String rowSignature,
        String clientHash,
        String observedVia,
        @NotNull Instant capturedAtUtc
) {
}
