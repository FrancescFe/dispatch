# Dispatch

Spring Boot service that consumes `order.created` events from Kafka and publishes JSON events to:

- `order.dispatched`
- `dispatch.tracking`

## Requirements

- Java 21
- Maven 3.9+
- Kafka running on `localhost:9092`

## Run

```bash
mvn spring-boot:run
```

## Test

```bash
mvn test
```

## Topics

- `order.created`: consumed by this service
- `order.dispatched`: produced by this service
- `dispatch.tracking`: produced by this service
