# Dispatch

Spring Boot service that consumes `order.created` events from Kafka and publishes JSON events to:

- `order.dispatched`
- `dispatch.tracking`

## Requirements

- Java 25
- Maven 3.9+
- Kafka running on `localhost:9092` for local execution without Docker

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

## Topics

- `order.created`: consumed by this service
- `order.dispatched`: produced by this service
- `dispatch.tracking`: produced by this service

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
  --topic order.created
```

### Producer Event Example

```json
{"orderId": "26b6f2b1-cc22-42f8-8285-82b8d309d1ae", "item": "item-1"}
```
