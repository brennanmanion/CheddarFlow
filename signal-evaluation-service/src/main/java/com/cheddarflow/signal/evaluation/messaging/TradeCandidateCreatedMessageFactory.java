package com.cheddarflow.signal.evaluation.messaging;

import com.cheddarflow.shared.messaging.MessageTypes;
import com.cheddarflow.shared.messaging.optionsflow.OptionsFlowEnrichedMessage;
import com.cheddarflow.shared.messaging.trade.TradeCandidateCreatedMessage;
import com.cheddarflow.signal.evaluation.model.SignalEvaluationRecord;
import com.cheddarflow.signal.evaluation.model.TradeCandidateRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class TradeCandidateCreatedMessageFactory {
    public TradeCandidateCreatedMessage create(
            SignalEvaluationRecord signalEvaluationRecord,
            TradeCandidateRecord tradeCandidateRecord,
            OptionsFlowEnrichedMessage enrichedMessage
    ) {
        return new TradeCandidateCreatedMessage(
                UUID.randomUUID(),
                MessageTypes.TRADE_CANDIDATE_CREATED,
                1,
                Instant.now(),
                enrichedMessage.sourceSessionId(),
                enrichedMessage.normalizedEventId(),
                enrichedMessage.symbol(),
                "trade-candidate-created:" + signalEvaluationRecord.normalizedEventId(),
                tradeCandidateRecord.id(),
                signalEvaluationRecord.id(),
                signalEvaluationRecord.strategyName(),
                signalEvaluationRecord.action(),
                enrichedMessage.expiry(),
                enrichedMessage.strike(),
                enrichedMessage.putCall(),
                enrichedMessage.premiumNumeric(),
                signalEvaluationRecord.score(),
                signalEvaluationRecord.confidence(),
                null
        );
    }
}
