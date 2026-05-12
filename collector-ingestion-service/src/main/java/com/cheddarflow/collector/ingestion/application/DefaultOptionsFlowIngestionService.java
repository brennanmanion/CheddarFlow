package com.cheddarflow.collector.ingestion.application;

import com.cheddarflow.collector.ingestion.api.BrowserRawEventRequest;
import com.cheddarflow.collector.ingestion.api.IngestionAcceptedResponse;
import com.cheddarflow.collector.ingestion.mapping.RequestMapper;
import com.cheddarflow.collector.ingestion.messaging.OptionsFlowCapturedMessageFactory;
import com.cheddarflow.collector.ingestion.parsing.OptionsFlowRowParser;
import com.cheddarflow.collector.ingestion.parsing.ParsedOptionsFlowRow;
import com.cheddarflow.collector.ingestion.persistence.CollectorIngestionRepository;
import com.cheddarflow.shared.domain.optionsflow.NormalizedOptionsFlowEvent;
import com.cheddarflow.shared.domain.optionsflow.RawBrowserEvent;
import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import com.cheddarflow.shared.messaging.optionsflow.OptionsFlowCapturedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DefaultOptionsFlowIngestionService implements OptionsFlowIngestionService {
    private static final Logger log = LoggerFactory.getLogger(DefaultOptionsFlowIngestionService.class);
    private static final String RAW_EVENT_DEDUPLICATED_DETAIL = "Raw browser event deduplicated; normalized event and outbox entry not rewritten.";

    private final RequestMapper requestMapper;
    private final OptionsFlowRowParser optionsFlowRowParser;
    private final CollectorIngestionRepository collectorIngestionRepository;
    private final OptionsFlowCapturedMessageFactory messageFactory;
    private final ObjectMapper objectMapper;

    public DefaultOptionsFlowIngestionService(
            RequestMapper requestMapper,
            OptionsFlowRowParser optionsFlowRowParser,
            CollectorIngestionRepository collectorIngestionRepository,
            OptionsFlowCapturedMessageFactory messageFactory,
            ObjectMapper objectMapper
    ) {
        this.requestMapper = requestMapper;
        this.optionsFlowRowParser = optionsFlowRowParser;
        this.collectorIngestionRepository = collectorIngestionRepository;
        this.messageFactory = messageFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public IngestionAcceptedResponse ingest(BrowserRawEventRequest request) {
        RawBrowserEvent rawBrowserEvent = requestMapper.toRawBrowserEvent(request);
        collectorIngestionRepository.upsertCollectorRun(
                rawBrowserEvent.sessionId(),
                rawBrowserEvent.collectorType(),
                rawBrowserEvent.pageUrl(),
                rawBrowserEvent.pageTitle(),
                rawBrowserEvent.capturedAtUtc(),
                null,
                "RUNNING"
        );

        boolean inserted = collectorIngestionRepository.insertRawBrowserEvent(rawBrowserEvent);
        if (!inserted) {
            log.info(
                    "Deduplicated raw browser event session={} collectorType={} domKey={} capturedAt={}",
                    rawBrowserEvent.sessionId(),
                    rawBrowserEvent.collectorType(),
                    rawBrowserEvent.domKey(),
                    rawBrowserEvent.capturedAtUtc()
            );
            return IngestionAcceptedResponse.accepted(RAW_EVENT_DEDUPLICATED_DETAIL);
        }

        ParsedOptionsFlowRow parsedRow;
        try {
            parsedRow = optionsFlowRowParser.parse(rawBrowserEvent.sourceHtml());
        } catch (RuntimeException exception) {
            collectorIngestionRepository.insertIngestionError(
                    rawBrowserEvent.sessionId(),
                    "NORMALIZE_OPTIONS_ROW_FAILED",
                    buildErrorDetail(exception),
                    serializePayloadSafely(request)
            );
            log.warn(
                    "Persisted raw browser event but failed to normalize session={} collectorType={} rawEventId={}",
                    rawBrowserEvent.sessionId(),
                    rawBrowserEvent.collectorType(),
                    rawBrowserEvent.id(),
                    exception
            );
            return IngestionAcceptedResponse.accepted(
                    "Raw browser event persisted; normalization failed and was recorded."
            );
        }

        NormalizedOptionsFlowEvent normalizedEvent = requestMapper.toNormalizedOptionsFlowEvent(rawBrowserEvent, parsedRow);
        collectorIngestionRepository.insertNormalizedOptionsFlowEvent(normalizedEvent);

        OptionsFlowCapturedMessage message = messageFactory.create(normalizedEvent);
        collectorIngestionRepository.insertOutboxEvent(toOutboxEvent(normalizedEvent, message));

        log.info(
                "Persisted raw browser event session={} collectorType={} rawEventId={} normalizedEventId={}",
                rawBrowserEvent.sessionId(),
                rawBrowserEvent.collectorType(),
                rawBrowserEvent.id(),
                normalizedEvent.id()
        );

        return IngestionAcceptedResponse.accepted(
                "Raw browser event persisted, normalized, and written to the outbox."
        );
    }

    private OutboxEvent toOutboxEvent(
            NormalizedOptionsFlowEvent normalizedEvent,
            OptionsFlowCapturedMessage message
    ) {
        return new OutboxEvent(
                message.eventId(),
                "normalized_options_flow_event",
                normalizedEvent.id(),
                message.eventType(),
                message.eventVersion(),
                message.idempotencyKey(),
                serializePayloadSafely(message),
                serializePayloadSafely(buildHeaders(normalizedEvent, message)),
                message.occurredAtUtc(),
                Instant.now(),
                null,
                "PENDING",
                0,
                null
        );
    }

    private Map<String, Object> buildHeaders(
            NormalizedOptionsFlowEvent normalizedEvent,
            OptionsFlowCapturedMessage message
    ) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("message_type", message.eventType());
        headers.put("collector_type", "options_flow");
        headers.put("source_session_id", normalizedEvent.sessionId());
        headers.put("normalized_event_id", normalizedEvent.id());
        headers.put("raw_event_id", normalizedEvent.rawEventId());
        return headers;
    }

    private String serializePayloadSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON payload", exception);
        }
    }

    private String buildErrorDetail(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }
}
