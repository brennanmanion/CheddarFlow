# CODEX

## Working Mode

This repository is moving toward a Java-first architecture.

## Technology Decisions

- backend services are written in Java
- application framework is Spring Boot
- messaging is queue-based and should use a Java-friendly broker
- the preferred build layout is a multi-module Java project
- Python and one-off JavaScript files in this repo are legacy prototypes unless explicitly retained for validation

## Important Exception

The browser collector cannot be pure Java because Chrome extensions run in the browser runtime.

Therefore:

- browser extension code is allowed to remain JavaScript or TypeScript
- all server-side ingestion, enrichment, signal, and execution services should be implemented in Java with Spring Boot

## Current Direction

We are building toward this shape:

1. browser extension captures live CheddarFlow flow data
2. Spring Boot ingestion service stores raw and normalized events
3. queue-based Spring Boot workers enrich those events with outside market data
4. queue-based Spring Boot workers evaluate whether an actionable trade exists
5. later, a separate execution service may consume approved trade signals

## Engineering Rules

- do not add new production backend logic in Python
- do not add new production orchestration in Node.js
- keep business logic and persistence logic in Java services
- treat the current Python collector service as a stepping stone, not the target end state
- design all queue consumers to be idempotent
- use durable storage as the system of record
- publish work to queues only after the related database transaction is committed

## Default Stack For New Backend Work

- Java 21
- Spring Boot 3
- Spring Web
- Spring Actuator
- Spring Data JDBC or JPA as appropriate
- Flyway for schema migrations
- Testcontainers for integration tests

## Messaging Direction

Preferred broker direction:

- ActiveMQ Artemis is the default recommendation for Java/Spring Boot alignment

RabbitMQ is still acceptable if a clear operational reason appears later, but the current default planning assumption is ActiveMQ Artemis.
