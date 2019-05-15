# UBIRCH EVENT LOG KAFKA DISPATCHER

A service that allows to dispatch EventLog messages to their corresponding topics for further processing.

The dispatching is based on the EventLog Category and it is a json-based definition.

```json
[
  {
    "category": "UPP",
    "topics": [
      {
        "name": "com.ubirch.eventlog",
        "data_to_send": null
      },
      {
        "name": "com.ubirch.chainer.slave",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "SLAVE_TREE",
    "topics": [
      {
        "name": "com.ubirch.eventlog",
        "data_to_send": null
      },
      {
        "name": "com.ubirch.chainer.master",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "MASTER_TREE",
    "topics": [
      {
        "name": "com.ubirch.eventlog",
        "data_to_send": null
      },
      {
        "name": "com.ubirch.ethereum.input.bin",
        "data_to_send": "id"
      }
    ]
  },
  {
    "category": "ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK",
    "topics": [
      {
        "name": "com.ubirch.eventlog",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
    "topics": [
      {
        "name": "com.ubirch.eventlog",
        "data_to_send": null
      }
    ]
  }
]
```

