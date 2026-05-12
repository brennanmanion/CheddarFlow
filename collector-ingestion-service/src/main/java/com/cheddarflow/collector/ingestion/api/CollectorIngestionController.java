package com.cheddarflow.collector.ingestion.api;

import com.cheddarflow.collector.ingestion.application.CollectorHeartbeatService;
import com.cheddarflow.collector.ingestion.application.OptionsFlowIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CollectorIngestionController {
    private final OptionsFlowIngestionService optionsFlowIngestionService;
    private final CollectorHeartbeatService collectorHeartbeatService;

    public CollectorIngestionController(
            OptionsFlowIngestionService optionsFlowIngestionService,
            CollectorHeartbeatService collectorHeartbeatService
    ) {
        this.optionsFlowIngestionService = optionsFlowIngestionService;
        this.collectorHeartbeatService = collectorHeartbeatService;
    }

    @PostMapping("/options-flow/raw")
    public ResponseEntity<IngestionAcceptedResponse> ingestOptionsFlowRaw(
            @Valid @RequestBody BrowserRawEventRequest request
    ) {
        return ResponseEntity.accepted().body(optionsFlowIngestionService.ingest(request));
    }

    @PostMapping("/heartbeats")
    public ResponseEntity<IngestionAcceptedResponse> ingestHeartbeat(
            @Valid @RequestBody CollectorHeartbeatRequest request
    ) {
        return ResponseEntity.accepted().body(collectorHeartbeatService.ingest(request));
    }
}
