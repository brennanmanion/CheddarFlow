# Collector Ingestion Service

This is the first planned Java/Spring Boot backend service for the CheddarFlow pipeline.

## Responsibilities

- receive raw browser events from the Chrome extension
- receive collector heartbeats
- map requests into shared domain models
- persist raw and normalized data into PostgreSQL
- create outbox rows in the same transaction

## Current State

This module is a scaffold, not the final production implementation.

It includes:

- Spring Boot service entry point
- REST endpoint skeletons
- request/response models
- shared domain dependencies
- shared message contract dependencies
- Flyway PostgreSQL baseline schema

## Planned Endpoints

- `POST /api/options-flow/raw`
- `POST /api/heartbeats`

## Database Direction

Flyway migrations are under:

- [src/main/resources/db/migration](/Users/brennan/Documents/CheddarFlow/collector-ingestion-service/src/main/resources/db/migration)

The baseline migration includes:

- `collector_runs`
- `collector_heartbeats`
- `raw_browser_events`
- `normalized_options_flow_events`
- `ingestion_errors`
- `outbox_events`
