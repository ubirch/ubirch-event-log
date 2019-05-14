# UBIRCH EVENT LOG KAFKA DISPATCHER

A service that allows to dispatch EventLog messages to their corresponding topics for further processing.

The dispatching is based on the EventLog Category and it is a json-based definition.

```json

[
  {
    "category": "UPP",
    "topics": [
      "com.ubirch.eventlog.lookup_request",
      "com.ubirch.eventlog.lookup_request1"
    ]
  },
  {
    "category": "SLAVE_TREE",
    "topics": [
      "com.ubirch.eventlog.lookup_request"
    ]
  }
]

```

