package com.cheddarflow.signal.evaluation.application;

import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import com.cheddarflow.shared.messaging.MessageDestinations;
import com.cheddarflow.shared.messaging.optionsflow.OptionsFlowEnrichedMessage;
import com.cheddarflow.shared.messaging.trade.TradeCandidateCreatedMessage;
import com.cheddarflow.signal.evaluation.messaging.TradeCandidateCreatedMessageFactory;
import com.cheddarflow.signal.evaluation.model.SignalEvaluationRecord;
import com.cheddarflow.signal.evaluation.model.TradeCandidateRecord;
import com.cheddarflow.signal.evaluation.persistence.SignalEvaluationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OptionsFlowEnrichedConsumer {
    private static final Logger log = LoggerFactory.getLogger(OptionsFlowEnrichedConsumer.class);

    private final ObjectMapper objectMapper;
    private final SignalEvaluationRepository repository;
    private final TradeCandidateCreatedMessageFactory messageFactory;
    private final BigDecimal candidatePremiumThreshold;

    public OptionsFlowEnrichedConsumer(
            ObjectMapper objectMapper,
            SignalEvaluationRepository repository,
            TradeCandidateCreatedMessageFactory messageFactory,
            @Value("${cheddarflow.signal.candidate-premium-threshold:1000000}") BigDecimal candidatePremiumThreshold
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.messageFactory = messageFactory;
        this.candidatePremiumThreshold = candidatePremiumThreshold;
    }

    @JmsListener(destination = MessageDestinations.OPTIONS_FLOW_ENRICHED)
    @Transactional
    public void handle(String payloadJson) throws JsonProcessingException {
        OptionsFlowEnrichedMessage enrichedMessage = objectMapper.readValue(payloadJson, OptionsFlowEnrichedMessage.class);
        BigDecimal premiumNumeric = enrichedMessage.premiumNumeric() == null ? BigDecimal.ZERO : enrichedMessage.premiumNumeric();
        BigDecimal score = premiumNumeric.divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP);
        BigDecimal confidence = score.divide(BigDecimal.valueOf(2L), 6, RoundingMode.HALF_UP).min(BigDecimal.valueOf(0.99));
        boolean actionable = premiumNumeric.compareTo(candidatePremiumThreshold) >= 0;
        String action = resolveAction(enrichedMessage.putCall());

        SignalEvaluationRecord signalEvaluationRecord = new SignalEvaluationRecord(
                UUID.randomUUID(),
                enrichedMessage.sourceSessionId(),
                enrichedMessage.normalizedEventId(),
                enrichedMessage.enrichmentRecordId(),
                enrichedMessage.symbol(),
                "premium-threshold-v1",
                action,
                score,
                confidence,
                actionable ? "CANDIDATE_CREATED" : "NO_ACTION",
                actionable
                        ? "Premium exceeded candidate threshold before external enrichers were attached."
                        : "Premium did not exceed candidate threshold.",
                "signal-evaluation:" + enrichedMessage.normalizedEventId(),
                Instant.now(),
                Instant.now()
        );

        boolean inserted = repository.insertSignalEvaluation(signalEvaluationRecord);
        if (!inserted) {
            log.info(
                    "Skipped duplicate signal evaluation for normalizedEventId={}",
                    enrichedMessage.normalizedEventId()
            );
            return;
        }

        if (!actionable) {
            log.info(
                    "Stored non-actionable signal evaluation id={} normalizedEventId={} premium={}",
                    signalEvaluationRecord.id(),
                    signalEvaluationRecord.normalizedEventId(),
                    premiumNumeric
            );
            return;
        }

        TradeCandidateRecord tradeCandidateRecord = new TradeCandidateRecord(
                UUID.randomUUID(),
                signalEvaluationRecord.id(),
                enrichedMessage.sourceSessionId(),
                enrichedMessage.normalizedEventId(),
                enrichedMessage.symbol(),
                signalEvaluationRecord.action(),
                signalEvaluationRecord.score(),
                signalEvaluationRecord.confidence(),
                "CREATED",
                Instant.now()
        );
        repository.insertTradeCandidate(tradeCandidateRecord);

        TradeCandidateCreatedMessage candidateMessage = messageFactory.create(
                signalEvaluationRecord,
                tradeCandidateRecord,
                enrichedMessage
        );
        repository.insertOutboxEvent(
                new OutboxEvent(
                        candidateMessage.eventId(),
                        "trade_candidate",
                        tradeCandidateRecord.id(),
                        candidateMessage.eventType(),
                        candidateMessage.eventVersion(),
                        candidateMessage.idempotencyKey(),
                        objectMapper.writeValueAsString(candidateMessage),
                        objectMapper.writeValueAsString(buildHeaders(candidateMessage)),
                        candidateMessage.occurredAtUtc(),
                        Instant.now(),
                        null,
                        "PENDING",
                        0,
                        null
                )
        );

        log.info(
                "Created trade candidate id={} normalizedEventId={} symbol={} score={}",
                tradeCandidateRecord.id(),
                tradeCandidateRecord.normalizedEventId(),
                tradeCandidateRecord.symbol(),
                tradeCandidateRecord.score()
        );
    }

    private String resolveAction(String putCall) {
        return "Put".equalsIgnoreCase(putCall) ? "BUY_PUT" : "BUY_CALL";
    }

    private Map<String, Object> buildHeaders(TradeCandidateCreatedMessage message) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("message_type", message.eventType());
        headers.put("normalized_event_id", message.normalizedEventId());
        headers.put("source_session_id", message.sourceSessionId());
        return headers;
    }
}
