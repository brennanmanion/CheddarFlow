package com.cheddarflow.market.enrichment.outbox;

import com.cheddarflow.market.enrichment.persistence.MarketEnrichmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelayScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final MarketEnrichmentRepository repository;
    private final OutboxEventPublisher publisher;
    private final int batchSize;

    public OutboxRelayScheduler(
            MarketEnrichmentRepository repository,
            OutboxEventPublisher publisher,
            @Value("${cheddarflow.outbox.batch-size:25}") int batchSize
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${cheddarflow.outbox.relay-delay-ms:1000}")
    @Transactional
    public void relayPendingEvents() {
        List<PendingOutboxEvent> pendingEvents = repository.findPendingOutboxEvents(batchSize);
        for (PendingOutboxEvent pendingEvent : pendingEvents) {
            try {
                publisher.publish(pendingEvent);
                repository.markOutboxEventPublished(pendingEvent.id());
            } catch (Exception exception) {
                repository.recordOutboxPublishFailure(pendingEvent.id(), summarize(exception));
                log.warn(
                        "Failed to publish outbox event id={} type={} attempt={}",
                        pendingEvent.id(),
                        pendingEvent.eventType(),
                        pendingEvent.attemptCount() + 1,
                        exception
                );
            }
        }
    }

    private String summarize(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }
}
