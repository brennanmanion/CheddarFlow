package com.cheddarflow.trade.execution.messaging;

import com.cheddarflow.shared.messaging.MessageTypes;
import com.cheddarflow.shared.messaging.trade.TradeCandidateCreatedMessage;
import com.cheddarflow.shared.messaging.trade.TradeExecutionRequestedMessage;
import com.cheddarflow.trade.execution.model.ExecutionWorkItemRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class TradeExecutionRequestedMessageFactory {
    public TradeExecutionRequestedMessage create(
            ExecutionWorkItemRecord executionWorkItemRecord,
            TradeCandidateCreatedMessage candidateMessage
    ) {
        return new TradeExecutionRequestedMessage(
                UUID.randomUUID(),
                MessageTypes.TRADE_EXECUTION_REQUESTED,
                1,
                Instant.now(),
                candidateMessage.sourceSessionId(),
                candidateMessage.normalizedEventId(),
                candidateMessage.symbol(),
                "trade-execution-requested:" + candidateMessage.normalizedEventId(),
                executionWorkItemRecord.tradeCandidateId(),
                candidateMessage.action(),
                candidateMessage.expiry(),
                candidateMessage.strike(),
                candidateMessage.putCall(),
                candidateMessage.premiumNumeric(),
                candidateMessage.score(),
                candidateMessage.confidence(),
                executionWorkItemRecord.routingMode(),
                executionWorkItemRecord.status()
        );
    }
}
