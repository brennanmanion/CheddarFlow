package com.cheddarflow.collector.ingestion.application;

import com.cheddarflow.collector.ingestion.api.CollectorHeartbeatRequest;
import com.cheddarflow.collector.ingestion.api.IngestionAcceptedResponse;
import com.cheddarflow.collector.ingestion.mapping.RequestMapper;
import com.cheddarflow.collector.ingestion.persistence.CollectorIngestionRepository;
import com.cheddarflow.shared.domain.collector.CollectorHeartbeat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DefaultCollectorHeartbeatService implements CollectorHeartbeatService {
    private static final Logger log = LoggerFactory.getLogger(DefaultCollectorHeartbeatService.class);

    private final RequestMapper requestMapper;
    private final CollectorIngestionRepository collectorIngestionRepository;

    public DefaultCollectorHeartbeatService(
            RequestMapper requestMapper,
            CollectorIngestionRepository collectorIngestionRepository
    ) {
        this.requestMapper = requestMapper;
        this.collectorIngestionRepository = collectorIngestionRepository;
    }

    @Override
    @Transactional
    public IngestionAcceptedResponse ingest(CollectorHeartbeatRequest request) {
        CollectorHeartbeat heartbeat = requestMapper.toCollectorHeartbeat(request);
        collectorIngestionRepository.upsertCollectorRun(
                heartbeat.sessionId(),
                heartbeat.collectorType(),
                heartbeat.pageUrl(),
                heartbeat.pageTitle(),
                heartbeat.heartbeatAtUtc(),
                heartbeat.heartbeatAtUtc(),
                determineRunStatus(heartbeat)
        );
        collectorIngestionRepository.insertCollectorHeartbeat(UUID.randomUUID(), heartbeat);
        log.info(
                "Persisted collector heartbeat session={} attached={} captured={} stale={}",
                heartbeat.sessionId(),
                heartbeat.attached(),
                heartbeat.capturedEventCount(),
                heartbeat.stale()
        );

        return IngestionAcceptedResponse.accepted(
                "Collector heartbeat persisted."
        );
    }

    private String determineRunStatus(CollectorHeartbeat heartbeat) {
        if (heartbeat.stale()) {
            return "STALE";
        }
        if (heartbeat.attached()) {
            return "ATTACHED";
        }
        return "WAITING";
    }
}
