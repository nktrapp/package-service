# package-service

Spring Boot microservice for package management. Independent project (root dir excluded).

## Commands
- Build + test: `./gradlew build`
- Test only: `./gradlew test`
- Run full local stack: `docker compose up` (service on :8081, Mongo :27017, MiniStack :4566, mongo-express :8888)

## Architecture (hexagonal, 4 Gradle modules)
`domain` → `core` → `infrastructure` → `app`. Dependencies point inward.
- `domain`: pure Java + Jakarta Validation only — NO Spring imports. Entities, events, ports.
- `core`: use cases + DTOs; depends only on `domain` (+ spring-tx).
- `infrastructure`: Mongo/SQS adapters, `UseCaseConfig` (wires use cases as `@Bean` — manual, not @ComponentScan).
- `app`: `PackageServiceApplication`, controllers, `GlobalExceptionHandler` (RFC-7807 ProblemDetail).

## Messaging (transactional outbox/inbox over SQS FIFO)
- Producers write entity + outbox row in one transaction; a `@Scheduled` relay publishes and retries.
- Consumer = `SqsPackageEventListener` → `ProcessRouteCalculatedUseCase`; idempotency via inbox unique `eventId`.
- FIFO: `MessageGroupId = packageId`, `MessageDeduplicationId = eventId`. Queues end in `.fifo`.
- Event flow: emits `package.created` / `package.status.updated` / `package.destination.changed`; consumes `route.calculated` / `route.recalculated`.

## Gotchas
- **Mongo MUST be a replica set.** `MongoTransactionSupportVerifier` fails startup otherwise. `compose.yml` runs a single-node RS; URIs use `directConnection=true`.
- New use case → add a `@Bean` in `UseCaseConfig` (no component scanning of use cases).
- Package `status` is a strict state machine (`Package.withStatus`); a destination change goes `... → ROUTE_PENDING`.
- JDK 25: builds pin the JUnit BOM + `net.bytebuddy.experimental=true` (in `build.gradle.kts`) — required, don't remove.
- New code: prefer explicit types over `var`.

## Infra
Per-service Terraform in `terraform/` (consumes shared `terraform/base/` via remote state). package-service uses NO Redis.
