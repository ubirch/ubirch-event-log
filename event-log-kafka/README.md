# UBIRCH EVENT LOG KAFKA

Abstractions for creating consumers and producers.

## Consumer Runner

The purpose of the consumer runner is to provide a simple an straightforward abstraction
for creating and managing a kakfa consumer.

The principal elements of the consumer runner environment are:

1. **Configs:** A convenience to manage the configuration keys that are used to initialize the kafka consumer.

2. **ConsumerRecordsController:** Represents the the type that is actually processes the consumer records. 
The consumer doesn't care about how it is processed, it can be with Futures, Actors, as long as the result type matches.

3. **ConsumerRunner:** Represents a Consumer Runner for a Kafka Consumer. 
 It supports back-pressure using the pause/unpause. The pause duration is amortized.
 It supports plugging rebalance listeners.
 It supports autocommit and not autocommit.
 It supports commit attempts.
 It supports to "floors" for exception management. This is allows to escalate exceptions.
 It supports callbacks on the most relevant events.

4. **ProcessResult:** Represents the result that is expected result for the consumption. This is helpful to return the consumer record and an identifiable record.
This type is usually extended to support customized data.

5. **WithMetrics:** Adds prometheus support to consumer runners.

6. **Consumers:** There are out-of-the box consumers. A String and Bytes Consumers.

## Producer Runner

The purpose of the producer runner is to provide a simple and straightforward abstraction 
for creating a kafka producer.

The principal elements of the producer runner environment are:

1. **Configs:** A convenience to manage the configuration keys that are used to initialize the kafka producer.

2. **ProducerRunner:** Represents a simple definition for a kafka producer. It supports callback on the producer creation event

## Import into project

```xml
      <dependency>
            <groupId>com.ubirch</groupId>
            <artifactId>event-log-kafka</artifactId>
            <version>1.2.3-SNAPSHOT</version>
       </dependency>
```