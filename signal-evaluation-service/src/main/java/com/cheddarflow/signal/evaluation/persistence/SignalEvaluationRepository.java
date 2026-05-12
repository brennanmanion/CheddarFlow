package com.cheddarflow.signal.evaluation.persistence;

import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import com.cheddarflow.signal.evaluation.model.SignalEvaluationRecord;
import com.cheddarflow.signal.evaluation.model.TradeCandidateRecord;
import com.cheddarflow.signal.evaluation.outbox.PendingOutboxEvent;

import java.util.List;
import java.util.UUID;

public interface SignalEvaluationRepository {
    boolean insertSignalEvaluation(SignalEvaluationRecord signalEvaluationRecord);

    void insertTradeCandidate(TradeCandidateRecord tradeCandidateRecord);

    void insertOutboxEvent(OutboxEvent outboxEvent);

    List<PendingOutboxEvent> findPendingOutboxEvents(int limit);

    void markOutboxEventPublished(UUID outboxEventId);

    void recordOutboxPublishFailure(UUID outboxEventId, String errorDetail);
}
