# UBIRCH EVENT LOG
A service which collects events from other services and stores them into the database.

The way this software collects events is consuming them from Kafka. The database used here is Cassandra.

# System Components

1. Cluster: Controls the cassandra cluster.
2. Config: Controls the configuration provision.
3. Execution: Controls the execution context provision.
4. Kafka

    * Consumer: Controls the consumption of messages from Kafka

    * Process Executor: Pipeline for applying transformations to the incoming messages.

5. Life Cycle: Controls the hooks that should be run at application shutdown.

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

* [Ubirch Event Log](https://github.com/ubirch/ubirch-event-log)

    Make you you have cloned the application.

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

* __Kafka__:

Create a [topic](https://kafka.apache.org/quickstart#quickstart_createtopic).
You can customize the name by changing it in src/main/resources/application.conf or with the
withTopic helper on the created consumer.

## Build

To build the application run the following command

```
    mvn package
```

## Run

```
java -jar target/event-log-1.0-SNAPSHOT.jar
```

You can now start sending messages of the expected structure to the topic in kafka. The system should start storing them in Cassandra.

## Considerations

* __Message Structure__:

The following structure represents the data that the system expects.

```
{
   "event":{
      "id":"fdfef472-2a70-488f-8cc9-2691ab36dd54",
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
      "event_time":"2018-12-17T23:20:00.651Z",
      "event_time_info":{
         "year":2018,
         "month":12,
         "day":12,
         "hour":12,
         "minute":12,
         "second":12,
         "milli":12
      }
   },
   "signature":"this is a signature",
   "created":"2018-12-17T23:20:00.654Z",
   "updated":"2018-12-17T23:20:00.654Z"
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
mvn test -Dsuites=com.ubirch.services.kafka.ExecutorSpec
```

