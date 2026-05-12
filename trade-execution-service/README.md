# trade-execution-service

Consumes `trade-candidate.created` events from ActiveMQ Artemis, persists execution work items to PostgreSQL, and emits `trade-execution.requested` through a transactional outbox.

Current scope:

- queue execution work items after signal evaluation
- preserve contract metadata needed for later broker integration
- keep routing in `PAPER_REVIEW` mode by default

This service does not submit broker orders yet.
