# UBIRCH EVENT LOG

A service which collects events from other services and stores them into the database.

The way this software collects events is consuming them from Kafka. The database used here is Cassandra.
An SDK is also provided for easier creation of log from the services.

* [Event Log Core](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-core)

* [Event Log Service](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-service)

* [Event Log SDK](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)

![Event Log Components](https://raw.githubusercontent.com/ubirch/ubirch-event-log/1.2.x/.images/generalParts1.2.0.png "Event Log Components")


## Helper Libs

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


## Events Log Info

### Chainer

Creates an EventLog with following info

**Id**: The merkle tree root hash

**Event**: The merkle tree

**Category**: SLAVE_TREE

**CustomerId**: ubirch

**ServiceClass**: ubirchChainerSlave

**LookupKeys**:

*Name*: slave-tree-id

*Category*: slave-tree-chainer

*Key*: The Eventlog Tree Id (root hash)

*Value*: A collection of the incoming UPP id (ubirch payload)


### Encoder

Encode non-event-log messages into their corresponding event-logs

***UPP with hint = 0***

Creates an Eventlog with the following info

**Id**: The ubirch protocol payload

**Event**: the ubirch protocol packet

**Category**: UPP

**CustomerId**: customerId value extracted from the messageenvelope context

**ServiceClass**: upp-event-log-entry

**LookupKeys**:

*Name*: signature

*Category*: UPP

*Key*: The ubirch protocol payload (id)

*Value*: Signature

*Name*: device-id

*Category*: DEVICE

*Key*: device id

*Value*: The ubirch protocol payload (id)

*Name*: upp-chain

*Category*: CHAIN

*Key*: The ubirch protocol payload (id)

*Value*: The ubirch protocol payload chain

***Blockchain Response***

Creates an EventLog witht following Info

**Id**: blockchain response tx id

**Event**: The blockchain response

**CustomerId**:

**Category**: Uppercase string of (blockchain + "_" + networkType + "_" + networkInfo) e.g: ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK"

**ServiceClass**:EventLogFromConsumerRecord
 
**LookupKeys**:

*Name*: Uppercase string of (blockchain + "_" + networkType + "_" + networkInfo) e.g: ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK"

*Category*: PUBLIC_CHAIN

*Key*: Tx id

*Value*: blockchain message a.k.a root hass/tree id


### Dispatcher


Dispatches eventlogs like so:

EventLogs with

**Category**:UPP

**Routed**: com.ubirch.eventlog/com.ubirch.chainer.slave


**Category**: SLAVE_TREE

**Routed**: com.ubirch.eventlog/com.ubirch.chainer.master/com.ubirch.ethereum.input.bin(only id)

**Category**:ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK

**Routed**: com.ubirch.eventlog










