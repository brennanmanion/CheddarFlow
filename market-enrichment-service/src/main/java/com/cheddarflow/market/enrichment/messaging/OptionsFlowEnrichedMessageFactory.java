package com.cheddarflow.market.enrichment.messaging;

import com.cheddarflow.market.enrichment.model.MarketEnrichmentRecord;
import com.cheddarflow.shared.messaging.MessageTypes;
import com.cheddarflow.shared.messaging.optionsflow.OptionsFlowCapturedMessage;
import com.cheddarflow.shared.messaging.optionsflow.OptionsFlowEnrichedMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OptionsFlowEnrichedMessageFactory {
    public OptionsFlowEnrichedMessage create(
            MarketEnrichmentRecord enrichmentRecord,
            OptionsFlowCapturedMessage capturedMessage
    ) {
        return new OptionsFlowEnrichedMessage(
                UUID.randomUUID(),
                MessageTypes.OPTIONS_FLOW_ENRICHED,
                1,
                Instant.now(),
                capturedMessage.sourceSessionId(),
                capturedMessage.normalizedEventId(),
                capturedMessage.symbol(),
                "options-flow-enriched:" + enrichmentRecord.normalizedEventId(),
                enrichmentRecord.id(),
                capturedMessage.expiry(),
                capturedMessage.strike(),
                capturedMessage.putCall(),
                capturedMessage.premiumNumeric(),
                null,
                null,
                enrichmentRecord.enrichmentStatus()
        );
    }
}
