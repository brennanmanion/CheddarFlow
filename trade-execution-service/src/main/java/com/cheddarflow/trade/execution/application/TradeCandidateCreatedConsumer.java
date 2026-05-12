package com.cheddarflow.trade.execution.application;

import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import com.cheddarflow.shared.messaging.MessageDestinations;
import com.cheddarflow.shared.messaging.trade.TradeCandidateCreatedMessage;
import com.cheddarflow.shared.messaging.trade.TradeExecutionRequestedMessage;
import com.cheddarflow.trade.execution.messaging.TradeExecutionRequestedMessageFactory;
import com.cheddarflow.trade.execution.model.ExecutionWorkItemRecord;
import com.cheddarflow.trade.execution.persistence.TradeExecutionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class TradeCandidateCreatedConsumer {
    private static final Logger log = LoggerFactory.getLogger(TradeCandidateCreatedConsumer.class);

    private final ObjectMapper objectMapper;
    private final TradeExecutionRepository repository;
    private final TradeExecutionRequestedMessageFactory messageFactory;
    private final String routingMode;

    public TradeCandidateCreatedConsumer(
            ObjectMapper objectMapper,
            TradeExecutionRepository repository,
            TradeExecutionRequestedMessageFactory messageFactory,
            @Value("${cheddarflow.execution.routing-mode:PAPER_REVIEW}") String routingMode
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.messageFactory = messageFactory;
        this.routingMode = routingMode;
    }

    @JmsListener(destination = MessageDestinations.TRADE_CANDIDATE_CREATED)
    @Transactional
    public void handle(String payloadJson) throws JsonProcessingException {
        TradeCandidateCreatedMessage candidateMessage = objectMapper.readValue(payloadJson, TradeCandidateCreatedMessage.class);
        UUID tradeCandidateId = resolveTradeCandidateId(candidateMessage);
        ExecutionWorkItemRecord workItemRecord = new ExecutionWorkItemRecord(
                UUID.randomUUID(),
                tradeCandidateId,
                candidateMessage.sourceSessionId(),
                candidateMessage.normalizedEventId(),
                candidateMessage.symbol(),
                candidateMessage.action(),
                candidateMessage.expiry(),
                candidateMessage.strike(),
                candidateMessage.putCall(),
                candidateMessage.premiumNumeric(),
                candidateMessage.score(),
                candidateMessage.confidence(),
                routingMode,
                "QUEUED",
                "Execution work item created from trade candidate. Broker routing remains disabled at this stage.",
                "trade-execution:" + candidateMessage.normalizedEventId(),
                Instant.now(),
                Instant.now()
        );

        boolean inserted = repository.insertExecutionWorkItem(workItemRecord);
        if (!inserted) {
            log.info(
                    "Skipped duplicate execution work item for tradeCandidateId={}",
                    tradeCandidateId
            );
            return;
        }

        TradeExecutionRequestedMessage executionRequestedMessage = messageFactory.create(workItemRecord, candidateMessage);
        repository.insertOutboxEvent(
                new OutboxEvent(
                        executionRequestedMessage.eventId(),
                        "execution_work_item",
                        workItemRecord.id(),
                        executionRequestedMessage.eventType(),
                        executionRequestedMessage.eventVersion(),
                        executionRequestedMessage.idempotencyKey(),
                        objectMapper.writeValueAsString(executionRequestedMessage),
                        objectMapper.writeValueAsString(buildHeaders(executionRequestedMessage)),
                        executionRequestedMessage.occurredAtUtc(),
                        Instant.now(),
                        null,
                        "PENDING",
                        0,
                        null
                )
        );

        log.info(
                "Queued execution work item id={} normalizedEventId={} symbol={} routingMode={}",
                workItemRecord.id(),
                workItemRecord.normalizedEventId(),
                workItemRecord.symbol(),
                workItemRecord.routingMode()
        );
    }

    private Map<String, Object> buildHeaders(TradeExecutionRequestedMessage message) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("message_type", message.eventType());
        headers.put("normalized_event_id", message.normalizedEventId());
        headers.put("source_session_id", message.sourceSessionId());
        headers.put("trade_candidate_id", message.tradeCandidateId());
        return headers;
    }

    private UUID resolveTradeCandidateId(TradeCandidateCreatedMessage candidateMessage) {
        if (candidateMessage.tradeCandidateId() != null) {
            return candidateMessage.tradeCandidateId();
        }
        return candidateMessage.signalEvaluationId();
    }
}
