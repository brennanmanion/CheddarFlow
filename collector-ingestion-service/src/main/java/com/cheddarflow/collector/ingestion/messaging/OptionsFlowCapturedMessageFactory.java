package com.cheddarflow.collector.ingestion.messaging;

import com.cheddarflow.shared.domain.optionsflow.NormalizedOptionsFlowEvent;
import com.cheddarflow.shared.messaging.MessageTypes;
import com.cheddarflow.shared.messaging.optionsflow.OptionsFlowCapturedMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OptionsFlowCapturedMessageFactory {
    public OptionsFlowCapturedMessage create(NormalizedOptionsFlowEvent event) {
        return new OptionsFlowCapturedMessage(
                UUID.randomUUID(),
                MessageTypes.OPTIONS_FLOW_CAPTURED,
                1,
                Instant.now(),
                event.sessionId(),
                event.id(),
                event.symbol(),
                "options-flow-captured:" + event.id(),
                event.rawEventId(),
                event.expiry(),
                event.strike(),
                event.putCall(),
                event.premiumNumeric(),
                event.capturedAtUtc()
        );
    }
}
