package com.cheddarflow.collector.ingestion.mapping;

import com.cheddarflow.collector.ingestion.api.BrowserRawEventRequest;
import com.cheddarflow.collector.ingestion.api.CollectorHeartbeatRequest;
import com.cheddarflow.collector.ingestion.parsing.ParsedOptionsFlowRow;
import com.cheddarflow.shared.domain.collector.CollectorHeartbeat;
import com.cheddarflow.shared.domain.optionsflow.NormalizedOptionsFlowEvent;
import com.cheddarflow.shared.domain.optionsflow.RawBrowserEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class RequestMapper {
    public RawBrowserEvent toRawBrowserEvent(BrowserRawEventRequest request) {
        return new RawBrowserEvent(
                UUID.randomUUID(),
                request.sessionId(),
                request.collectorType(),
                normalizeOptional(request.pageUrl()),
                normalizeOptional(request.pageTitle()),
                normalizeOptional(request.sourceSelector()),
                normalizeDomKey(request.domKey()),
                request.sourceHtml(),
                normalizeOptional(request.sourceText()),
                normalizeOptional(request.rowSignature()),
                sha256(request.sourceHtml()),
                normalizeOptional(request.clientHash()),
                normalizeOptional(request.observedVia()),
                request.capturedAtUtc(),
                Instant.now()
        );
    }

    public CollectorHeartbeat toCollectorHeartbeat(CollectorHeartbeatRequest request) {
        return new CollectorHeartbeat(
                request.sessionId(),
                request.collectorType(),
                normalizeOptional(request.pageUrl()),
                normalizeOptional(request.pageTitle()),
                request.attached(),
                request.attachAttempts(),
                request.queuedEventCount(),
                request.capturedEventCount(),
                request.duplicateCount(),
                request.parseFailureCount(),
                request.sendFailureCount(),
                request.lastCaptureAtUtc(),
                request.captureAgeSeconds(),
                request.stale(),
                normalizeOptional(request.staleReason()),
                normalizeOptional(request.sourceSelector()),
                normalizeOptional(request.reason()),
                request.heartbeatAtUtc()
        );
    }

    public NormalizedOptionsFlowEvent toNormalizedOptionsFlowEvent(RawBrowserEvent rawBrowserEvent, ParsedOptionsFlowRow parsedRow) {
        return new NormalizedOptionsFlowEvent(
                UUID.randomUUID(),
                rawBrowserEvent.id(),
                rawBrowserEvent.sessionId(),
                normalizeOptional(parsedRow.eventTimeText()),
                normalizeOptional(parsedRow.eventDateText()),
                normalizeOptional(parsedRow.symbol()),
                normalizeOptional(parsedRow.expiry()),
                normalizeOptional(parsedRow.strike()),
                normalizeOptional(parsedRow.putCall()),
                normalizeOptional(parsedRow.side()),
                normalizeOptional(parsedRow.buySell()),
                normalizeOptional(parsedRow.spot()),
                normalizeOptional(parsedRow.size()),
                normalizeOptional(parsedRow.price()),
                normalizeOptional(parsedRow.premiumText()),
                normalizeOptionalBigDecimal(parsedRow.premiumNumeric()),
                normalizeOptional(parsedRow.sweepBlockSplit()),
                normalizeOptional(parsedRow.volume()),
                normalizeOptional(parsedRow.openInterest()),
                normalizeOptional(parsedRow.conditions()),
                rawBrowserEvent.capturedAtUtc()
        );
    }

    private BigDecimal normalizeOptionalBigDecimal(BigDecimal value) {
        return value;
    }

    private String normalizeDomKey(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? "" : normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
