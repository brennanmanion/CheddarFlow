package com.cheddarflow.collector.ingestion.application;

import com.cheddarflow.collector.ingestion.api.CollectorHeartbeatRequest;
import com.cheddarflow.collector.ingestion.api.IngestionAcceptedResponse;

public interface CollectorHeartbeatService {
    IngestionAcceptedResponse ingest(CollectorHeartbeatRequest request);
}
