# UBIRCH EVENT LOG KAFKA DISPATCHER

A service that allows to dispatch EventLog messages to their corresponding topics for further processing.

The dispatching is based on the EventLog Category, and it is a json-based definition. Take a look at the configuration files where the route to this file is defined. 

```json
[
  {
    "category": "UPP",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "foundation"
        ],
        "name": "ubirch-svalbard-evt-foundation-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "UPP_DISABLE",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-update-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "UPP_ENABLE",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-update-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "UPP_DELETE",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-update-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "PUB_KEY",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "foundation"
        ],
        "name": "ubirch-svalbard-evt-foundation-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "SLAVE_TREE",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "aggregation"
        ],
        "name": "ubirch-svalbard-evt-aggregator-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "MASTER_TREE",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "blockchain"
        ],
        "name": "ubirch-svalbard-evt-anchor-mgt-string",
        "data_to_send": "id"
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "ETHEREUM_MAINNET_FRONTIER_MAINNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "IOTA_MAINNET_IOTA_MAINNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "ETHEREUM_TESTNNET_ETHERERUM_CLASSIC_TESTNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "ETHEREUM-CLASSIC_MAINNET_ETHERERUM_CLASSIC_MAINNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "ETHEREUM-CLASSIC_TESTNET_ETHERERUM_CLASSIC_KOTTI_TESTNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "GOV-DIGITAL_TESTNET_GOV_DIGITAL_TESTNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "GOV-DIGITAL_MAINNET_GOV_DIGITAL_MAINNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  },
  {
    "category": "BLOXBERG_TESTNET_BLOXBERG_TESTNET_NETWORK",
    "topics": [
      {
        "tags": [
          "storage"
        ],
        "name": "ubirch-svalbard-evt-log-json",
        "data_to_send": null
      },
      {
        "tags": [
          "discovery"
        ],
        "name": "ubirch-svalbard-evt-discovery-creator-json",
        "data_to_send": null
      }
    ]
  }
]
```
A yml version is shown below:


```yaml
- category: UPP
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [foundation]
      name: ubirch-svalbard-evt-foundation-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: PUB_KEY
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [foundation]
      name: ubirch-svalbard-evt-foundation-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: SLAVE_TREE
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [aggregation]
      name: ubirch-svalbard-evt-aggregator-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: MASTER_TREE
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [blockchain]
      name: ubirch-svalbard-evt-anchor-mgt-string
      data_to_send: id
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: ETHEREUM_MAINNET_FRONTIER_MAINNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: IOTA_TESTNET_IOTA_TESTNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: IOTA_MAINNET_IOTA_MAINNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: ETHEREUM_TESTNNET_ETHERERUM_CLASSIC_TESTNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: ETHEREUM-CLASSIC_MAINNET_ETHERERUM_CLASSIC_MAINNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: ETHEREUM-CLASSIC_TESTNET_ETHERERUM_CLASSIC_KOTTI_TESTNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: GOV-DIGITAL_TESTNET_GOV_DIGITAL_TESTNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: GOV-DIGITAL_MAINNET_GOV_DIGITAL_MAINNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
- category: BLOXBERG_TESTNET_BLOXBERG_TESTNET_NETWORK
  topics:
    - tags: [storage]
      name: ubirch-svalbard-evt-log-json
    - tags: [discovery]
      name: ubirch-svalbard-evt-discovery-creator-json
```

Note: The contents in this file might not represent the routes that are actually deployed.
The file is deployed to kubernetes as a config map.

## Tag Exclusion

The event-log also supports tag exclusions, which allows an eventlog structure to be filtered out from a dispatching rule. You can configure it by creating or adding a tag-excludes header. Example of tag exclusions are:

* tags-exclude:blockchain: Tag that won't anchor.
* tags-exclude:aggregation: Tag that won't aggregate
* tags-exclude:storage: Tag that won't store

`Nomenclature: "tag-exclude:" + tag name`

See example

```
           EventLog("upp-event-log-entry", Values.UPP_CATEGORY, payload)
            .withLookupKeys(signatureLookupKey ++ chainLookupKey ++ deviceLookupKey)
            .withCustomerId(customerId)
            .withRandomNonce
            .withNewId(payloadHash)
            .addHeadersIf(!fastChainEnabled, headerExcludeBlockChain)
            .addBlueMark
            .addTraceHeader(Values.ENCODER_SYSTEM)
```
