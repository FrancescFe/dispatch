# Dispatch

Spring Boot service that consumes `order.created` events from Kafka, checks item availability through an external stock service, and publishes JSON events to:

- `order.dispatched`
- `dispatch.tracking`
- `order.created-dlt` for non-retryable failures

## Requirements

- Java 25
- Maven 3.9+
- Kafka running on `localhost:9092` for local execution without Docker

## Flow

1. `Dispatch` consumes an `order.created` event.
2. `StockServiceClient` calls `dispatch.stockServiceEndpoint?item=<item>` to check availability.
3. If the stock service returns `true`, `Dispatch` publishes:
   - `DispatchPreparing` to `dispatch.tracking`
   - `OrderDispatched` to `order.dispatched`
   - `DispatchCompleted` to `dispatch.tracking`
4. If the stock service returns `false`, no outgoing Kafka messages are produced.

## Error Handling

- `StockServiceClient` wraps transient failures such as `5xx` responses and connection/access errors in `RetryableException`.
- Retryable exceptions are retried by the Kafka listener error handler with a fixed backoff.
- Non-retryable failures are sent to the dead letter topic `order.created-dlt`.
- The current dead letter topic name follows Spring Kafka's default naming convention: `<original-topic>-dlt`.

## Run

```bash
mvn spring-boot:run
```

## Run With Docker

```bash
docker compose up --build
```

This starts:

- Kafka on `localhost:9092`
- `dispatch` on `localhost:8080`

## External Stock Service

For real manual testing outside the automated integration tests, this service expects an external stock service to be available at `dispatch.stockServiceEndpoint`.

The current setup used in the course relies on the Lydtech standalone WireMock repository:

- https://github.com/lydtechconsulting/introduction-to-kafka-wiremock

That repository simulates the third-party stock service used by `Dispatch`. Its README documents the supported responses and how to run it on port `9001`.

This means:

- automated integration tests in this repository do not require downloading that project because they use embedded WireMock
- manual local execution of `dispatch` does require a compatible stock service running, such as the Lydtech WireMock project

## Topics

- `order.created`: consumed by this service
- `order.dispatched`: produced by this service with the same Kafka message key received in `order.created`
- `dispatch.tracking`: produced by this service with the same Kafka message key received in `order.created` and intended to be consumed by the `Tracking` service
- `order.created-dlt`: dead letter topic for non-retryable processing failures

## Testing the application (with docker)

### Consuming Topics Example

```bash
~/tools/kafka/kafka_2.13-4.2.0/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:29092 \
  --topic order.dispatched \
  --from-beginning
```

### Producing Topics Example

```bash
~/tools/kafka/kafka_2.13-4.2.0/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:29092 \
  --topic order.created \
  --reader-property parse.key=true \
  --reader-property key.separator=:
```

### Producer Event Example

```
my-key:{"orderId": "26b6f2b1-cc22-42f8-8285-82b8d309d1ae", "item": "item-1"}
```

### Consuming Topics Example

```bash
~/tools/kafka/kafka_2.13-4.2.0/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:29092 \
  --topic order.created \
  --formatter-property print.key=true \
  --formatter-property key.separator=:
```

## Integration

`Dispatch` publishes `dispatch.tracking` topic for the `Tracking` service and preserves the Kafka message key from the original `order.created` event in both outgoing topics.

Integration tests use:

- embedded Kafka for topic flow validation
- WireMock to simulate the external stock service used by `StockServiceClient`

The stock service endpoint is configured through `dispatch.stockServiceEndpoint`.

For manual end-to-end testing, the course uses the Lydtech standalone WireMock stock service repository:

- https://github.com/lydtechconsulting/introduction-to-kafka-wiremock

Tracking repository:

- https://github.com/FrancescFe/tracking (SpringBoot 4 + Kotlin + Gradle)

Expected `dispatch.tracking` payload:

```
my-key:{"orderId": "26b6f2b1-cc22-42f8-8285-82b8d309d1ae"}
```

## Notes

Kafka CLI usage notes:

- [docs/kafka-cli-notes.md](docs/kafka-cli-notes.md)
