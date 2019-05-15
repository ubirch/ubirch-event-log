# UBIRCH EVENT LOG

The Event Log System is a collection of services that allow the chaining

![Event Log Components](https://raw.githubusercontent.com/ubirch/ubirch-event-log/master/.images/FastChainer-Eventlog-Overview.png "Event Log System")

* [Event Log Service](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-service)

* [Event Log Dispatcher](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

* [Event Log Encoder](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

* [Event Log Chainer](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

* [Event Log Kafka Lookup](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

* [Event Log SDK](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

## Helper Libs

* [Event Log Core](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-core)

* [Event Log Kafka](https://github.com/ubirch/ubirch-event-log/tree/1.2.x/event-log-kafka)

* [Event Log Util](https://github.com/ubirch/ubirch-event-log/tree/1.2.x/event-log-util)


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










