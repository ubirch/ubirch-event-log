# UBIRCH EVENT LOG

The Event Log System is a collection of services that allow the chaining

![Event Log Components](https://raw.githubusercontent.com/ubirch/ubirch-event-log/master/.images/event_log_pipeline_architecture.png "Event Log System")

* [Event Log Service](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-service)

* [Event Log Dispatcher](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

* [Event Log Encoder](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

* [Event Log Chainer](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

* [Event Log Kafka Lookup](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

* [Event Log SDK](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

* [Event Log Discovery Creator](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-discovery-creator)

## Helper Libs

* [Event Log Core](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-core)

* [Event Log Kafka](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-kafka)

* [Event Log Util](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-util)


## Install

To build the application run the following command

```
    mvn install
```

After doing this, you can drill down to working on the
particular project you may be interested in.

## Useful Scripts

**migrateDb.sh** Helps in the migration of the db evolution scripts.

## Prometheus Metrics

**Note**: If you're starting the multiple services on the same machine/jvm, the port might change and
you will have to change it accordingly. The port that is assigned to Prometheus is show on the console of 
every service at boot.

```
  http://localhost:4321/
```

  or
   
```  
   watch -d "curl --silent http://localhost:4321 | grep ubirch"
```










