# UBIRCH EVENT LOG KAFKA DISPATCHER

A service that allows to dispatch EventLog messages to their corresponding topics for further processing.

The dispatching is based on the EventLog Category and it is a json-based definition.

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

