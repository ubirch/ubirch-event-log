/* Cassandra migration for keyspace event_log.
   Version 6 - 2019-10-09T11:29:51.938035+00:00

   Recreating events table with new clustering keys */

drop table if exists events;

create table if not exists events
(
    id            text,
    customer_id   text,
    service_class text,
    category      text,
    signature     text,
    event         text,
    year          int,
    month         int,
    day           int,
    hour          int,
    minute        int,
    second        int,
    milli         int,
    event_time    timestamp,
    nonce         text,
    PRIMARY KEY ((id, category), year, month, day, hour, minute, second, milli)
) WITH CLUSTERING ORDER BY (year DESC, month DESC, day DESC, hour DESC, minute DESC, second DESC, milli DESC);

