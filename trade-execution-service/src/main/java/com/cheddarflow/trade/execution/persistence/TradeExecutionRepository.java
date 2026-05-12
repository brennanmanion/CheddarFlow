package com.cheddarflow.trade.execution.persistence;

import com.cheddarflow.shared.domain.outbox.OutboxEvent;
import com.cheddarflow.trade.execution.model.ExecutionWorkItemRecord;
import com.cheddarflow.trade.execution.outbox.PendingOutboxEvent;

import java.util.List;
import java.util.UUID;

public interface TradeExecutionRepository {
    boolean insertExecutionWorkItem(ExecutionWorkItemRecord executionWorkItemRecord);

    void insertOutboxEvent(OutboxEvent outboxEvent);

    List<PendingOutboxEvent> findPendingOutboxEvents(int limit);

    void markOutboxEventPublished(UUID outboxEventId);

    void recordOutboxPublishFailure(UUID outboxEventId, String errorDetail);
}
