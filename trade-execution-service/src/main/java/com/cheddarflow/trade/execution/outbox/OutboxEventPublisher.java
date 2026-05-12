package com.cheddarflow.trade.execution.outbox;

import com.cheddarflow.shared.messaging.MessageDestinations;
import com.cheddarflow.shared.messaging.MessageTypes;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventPublisher {
    private final JmsTemplate jmsTemplate;

    public OutboxEventPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void publish(PendingOutboxEvent outboxEvent) {
        jmsTemplate.convertAndSend(resolveDestination(outboxEvent.eventType()), outboxEvent.payloadJson());
    }

    private String resolveDestination(String eventType) {
        return switch (eventType) {
            case MessageTypes.TRADE_EXECUTION_REQUESTED -> MessageDestinations.TRADE_EXECUTION_REQUESTED;
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + eventType);
        };
    }
}
