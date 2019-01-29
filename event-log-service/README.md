# UBIRCH EVENT LOG SERVICE

A service which collects events from other services and stores them into the database.

The way this software collects events is consuming them from Kafka. The database used here is Cassandra.

## Run

```
java -jar target/event-log-service-1.0-SNAPSHOT.jar
```

## Create Docker Image
```
mvn dockerfile:build
```