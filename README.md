# UBIRCH EVENT LOG

A service which collects events from other services and stores them into the database.

The way this software collects events is consuming them from Kafka. The database used here is Cassandra.
An SDK is also provided for easier creation of log from the services.

* [Event Log Core](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-core)

* [Event Log Service](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-service)

* [Event Log SDK](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

![Event Log Components](https://raw.githubusercontent.com/ubirch/ubirch-event-log/1.1.x/.images/generalParts1.1.0.png "Event Log Components")


## Helper Libs

* [Event Log Kafka](https://github.com/ubirch/ubirch-event-log/tree/1.1.x/event-log-kafka)

* [Event Log Util](https://github.com/ubirch/ubirch-event-log/tree/1.1.x/event-log-util)


## Install

To build the application run the following command

```
    mvn install
```

After doing this, you can drill down to working on the
particular project you may be interested in.

## Useful Scripts

**exportEnvVariables.sh**: Sets the environment vars for easily modification when running service directly from the jar.

**deleteEnvVariables.sh**: Removes the environment var. Very useful to use defaults again and run tests.

**migrateDb.sh** Helps in the migration of the db evolution scripts.

## Prometheus Metrics

```
  http://localhost:4321/
```

  or
   
```  
   watch -d "curl --silent http://localhost:4321 | grep ubirch"
```