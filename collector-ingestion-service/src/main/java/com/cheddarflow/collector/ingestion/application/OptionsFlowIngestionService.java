package com.cheddarflow.collector.ingestion.application;

import com.cheddarflow.collector.ingestion.api.BrowserRawEventRequest;
import com.cheddarflow.collector.ingestion.api.IngestionAcceptedResponse;

public interface OptionsFlowIngestionService {
    IngestionAcceptedResponse ingest(BrowserRawEventRequest request);
}
