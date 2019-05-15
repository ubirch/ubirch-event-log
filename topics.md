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
