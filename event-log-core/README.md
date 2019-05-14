# UBIRCH EVENT LOG CORE

A set of core elements that allow for collecting events from other services
and storing them into the database.

The way this software collects events is consuming them from Kafka. The database used here is Cassandra.

# System Components

## Process

__Process Executor__: Pipeline for applying transformations to the incoming messages.

## Services

1. __Cluster__: Controls the cassandra cluster.
2. __Cassandra Connection Service__: Controls the context needed to run queries against Cassandra.
2. __Config__: Controls the configuration provision.
3. __Execution__: Controls the execution context provision.
4. __Kafka__

    * __Consumer__: Controls the consumption of messages from Kafka
    * __Producer__: Controls the production of error messages to Kafka

5. __Life Cycle__: Controls the hooks that should be run at application shutdown.

# How to get started

## Software needed

Make sure you have the following systems up and running.

* [Apache Cassandra:](http://cassandra.apache.org/) Make sure you have a running instance of [Apache Cassandra](http://cassandra.apache.org/). You can follow the install instructions [here](http://cassandra.apache.org/doc/latest/getting_started/installing.html).
    At the time of this writing, the version being used is _3.11.3_

* [Apache Kafka Server:](https://kafka.apache.org/quickstart) Make sure you have a running instance of [Apache Kafka Server](https://kafka.apache.org/quickstart). You can follow the install instructions [here](https://kafka.apache.org/quickstart).
    At the time of this writing, the version being used is _2.X_

* [Cassandra Migrate:](https://github.com/Cobliteam/cassandra-migrate) Make sure you have installed this tool. This tool will be used to run the evolutions scripts of the database.
    You can find more information [here](https://github.com/ubirch/ubirch-cassandra-eval#how-to-run-httpsgithubcomcobliteamcassandra-migrate).

* [Maven:](https://maven.apache.org/) Make sure you have Maven installed. This tool is used for building the application.

* [ubirch Event Log](https://github.com/ubirch/ubirch-event-log)

    Make sure you have cloned the application and got it compiled.

## Configurations needed

* __Evolution Scripts__:

Run this to baseline the db.

```
    cassandra-migrate -H 127.0.0.1 -p 9042 baseline
```

Optionally, make sure the table database_migrations has been created.

```
    cassandra-migrate -H 127.0.0.1 -p 9042 status
```

Run the evolutions scripts that are stored in 'src/main/resources/db/migrations'

```
    cassandra-migrate -H 127.0.0.1 -p 9042 migrate
```
Optionally, make sure that the evolutions scripts have been successfully installed/applied

```
    cassandra-migrate -H 127.0.0.1 -p 9042 status
```

* __Kafka Topics__:

_Events to Log_

Create an events [topic](https://kafka.apache.org/quickstart#quickstart_createtopic).
You can customize the name by changing it in src/main/resources/application.conf or with the
withTopic helper on the created consumer. By default the name of topic that the application tries to connect to
is _com.ubirch.eventlog_ if none provided.

## Build

To build the application run the following command

```
    mvn package
```

You can now start sending messages of the expected structure to the topic in kafka. The system should start storing them in Cassandra.

## Considerations

* __Message Structure__:

The following structure represents the data that the system expects.

```json
{
   "id":"243f7063-6126-470e-9947-be49a62351c0",
   "service_class":"this is a service class",
   "category":"this is a category",
   "event":{
      "numbers":[
         1,
         2,
         3,
         4
      ]
   },
   "event_time":"2019-01-28T21:54:59.439Z",
   "signature":"this is a signature"
}
```


# Tests


```
mvn package
```

or

```
mvn test
```

or on suite in particular

```
mvn test -Dsuites=com.ubirch.services.kafka.consumer.ExecutorSpec
```

