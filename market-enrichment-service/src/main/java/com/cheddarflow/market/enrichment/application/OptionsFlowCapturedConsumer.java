package com.cheddarflow.market.enrichment.application;

import com.cheddarflow.market.enrichment.messaging.OptionsFlowEnrichedMessageFactory;
import com.cheddarflow.market.enrichment.model.MarketEnrichmentRecord;
import com.cheddarflow.market.enrichment.persistence.MarketEnrichmentRepository;
import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import com.cheddarflow.shared.messaging.MessageDestinations;
import com.cheddarflow.shared.messaging.optionsflow.OptionsFlowCapturedMessage;
import com.cheddarflow.shared.messaging.optionsflow.OptionsFlowEnrichedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OptionsFlowCapturedConsumer {
    private static final Logger log = LoggerFactory.getLogger(OptionsFlowCapturedConsumer.class);

    private final ObjectMapper objectMapper;
    private final MarketEnrichmentRepository repository;
    private final OptionsFlowEnrichedMessageFactory messageFactory;

    public OptionsFlowCapturedConsumer(
            ObjectMapper objectMapper,
            MarketEnrichmentRepository repository,
            OptionsFlowEnrichedMessageFactory messageFactory
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.messageFactory = messageFactory;
    }

    @JmsListener(destination = MessageDestinations.OPTIONS_FLOW_CAPTURED)
    @Transactional
    public void handle(String payloadJson) throws JsonProcessingException {
        OptionsFlowCapturedMessage capturedMessage = objectMapper.readValue(payloadJson, OptionsFlowCapturedMessage.class);
        MarketEnrichmentRecord enrichmentRecord = new MarketEnrichmentRecord(
                UUID.randomUUID(),
                capturedMessage.sourceSessionId(),
                capturedMessage.normalizedEventId(),
                capturedMessage.symbol(),
                capturedMessage.expiry(),
                capturedMessage.strike(),
                capturedMessage.putCall(),
                capturedMessage.premiumNumeric(),
                "PENDING_EXTERNAL_DATA",
                "NOT_QUERIED",
                "NOT_QUERIED",
                "NOT_QUERIED",
                "NOT_QUERIED",
                "Placeholder enrichment record created from captured options-flow event.",
                "market-enrichment:" + capturedMessage.normalizedEventId(),
                Instant.now(),
                Instant.now()
        );

        boolean inserted = repository.insertEnrichmentRecord(enrichmentRecord);
        if (!inserted) {
            log.info(
                    "Skipped duplicate enrichment record for normalizedEventId={}",
                    capturedMessage.normalizedEventId()
            );
            return;
        }

        OptionsFlowEnrichedMessage enrichedMessage = messageFactory.create(enrichmentRecord, capturedMessage);
        repository.insertOutboxEvent(
                new OutboxEvent(
                        enrichedMessage.eventId(),
                        "market_enrichment_record",
                        enrichmentRecord.id(),
                        enrichedMessage.eventType(),
                        enrichedMessage.eventVersion(),
                        enrichedMessage.idempotencyKey(),
                        objectMapper.writeValueAsString(enrichedMessage),
                        objectMapper.writeValueAsString(buildHeaders(enrichedMessage)),
                        enrichedMessage.occurredAtUtc(),
                        Instant.now(),
                        null,
                        "PENDING",
                        0,
                        null
                )
        );

        log.info(
                "Created enrichment record id={} normalizedEventId={} symbol={}",
                enrichmentRecord.id(),
                enrichmentRecord.normalizedEventId(),
                enrichmentRecord.symbol()
        );
    }

    private Map<String, Object> buildHeaders(OptionsFlowEnrichedMessage message) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("message_type", message.eventType());
        headers.put("normalized_event_id", message.normalizedEventId());
        headers.put("source_session_id", message.sourceSessionId());
        return headers;
    }
}
