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

* [Kafka Express](https://github.com/ubirch/ubirch-kafka-express)

## Prerequisites 

In order to run the applications, you will need a running instance of Kafka and Cassandra. 

Please refer to [DB Migrations](https://github.com/ubirch/ubirch-cassandra-eval#db-migrations-management) in order to run the
migration scripts.

## Install

To build the application run the following command:

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
  (1) http://localhost:4321/
```

  or
   
```  
   (2) watch -d "curl --silent http://localhost:4321 | grep SERVICE-NAME"
```

You can inspect the service name by running option (1) and finding "service" 












