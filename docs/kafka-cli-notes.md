# GENERAL

```
KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
```
```
bin/kafka-storage.sh format --standalone -t $KAFKA_CLUSTER_ID -c config/server.properties
```
```
bin/kafka-server-start.sh config/server.properties
```
```
bin/kafka-server-stop.sh
```

# run consumer/producer
```
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic my.topic
```
```
bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic my.topic
```

## run consumer/producer with key
```
bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic my.topic \
  --property parse.key=true \
  --property key.separator=:
```
```
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic my.topic \
  --property print.key=true \
  --property key.separator=:
```

# topics
```
bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```
```
bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic my.new.topic
```
```
bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic my.new.topic
```
```
bin/kafka-topics.sh --bootstrap-server localhost:9092 --alter --topic my.new.topic --partitions 3
```
```
bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic my.new.topic
```

# consumers
```
bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
```
```
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic cg.demo.topic --group my.new.group
```
```
bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group my.new.group --state
```
```
bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group my.new.group --members
```
