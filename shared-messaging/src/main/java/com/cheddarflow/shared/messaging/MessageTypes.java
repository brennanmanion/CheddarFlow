package com.cheddarflow.shared.messaging;

public final class MessageTypes {
    public static final String OPTIONS_FLOW_CAPTURED = "OptionsFlowCaptured";
    public static final String OPTIONS_FLOW_ENRICHED = "OptionsFlowEnriched";
    public static final String TRADE_CANDIDATE_CREATED = "TradeCandidateCreated";
    public static final String TRADE_EXECUTION_REQUESTED = "TradeExecutionRequested";

    private MessageTypes() {
    }
}
