# UBIRCH EVENT LOG

A service which collects events from other services and stores them into the database.

The way this software collects events is consuming them from Kafka. The database used here is Cassandra.
An SDK is also provided for easier creation of log from the services.

* [Event Log](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-core)

* [Event Log Service](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-service)

* [Event Log SDK](https://github.com/ubirch/ubirch-event-log/blob/master/event-log-sdk)


![Event Log Components](https://raw.githubusercontent.com/ubirch/ubirch-event-log/master/.images/generalParts.png "Event Log Components")
