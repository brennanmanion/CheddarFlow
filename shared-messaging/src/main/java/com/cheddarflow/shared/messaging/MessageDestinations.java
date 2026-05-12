package com.cheddarflow.shared.messaging;

public final class MessageDestinations {
    public static final String OPTIONS_FLOW_CAPTURED = "options-flow.captured";
    public static final String OPTIONS_FLOW_ENRICHED = "options-flow.enriched";
    public static final String TRADE_CANDIDATE_CREATED = "trade-candidate.created";
    public static final String TRADE_EXECUTION_REQUESTED = "trade-execution.requested";

    private MessageDestinations() {
    }
}
