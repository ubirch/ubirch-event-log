/* Cassandra migration for keyspace event_log.
   Version 2 - 2019-04-03T19:15:07.080862+00:00

   Recreating table with new constrains */

drop table if exists events;

create table if not exists events (
    id text,
    customer_id text,
    service_class text,
    category text,
    signature text,
    event text,
    year int ,
    month int,
    day int,
    hour int,
    minute int,
    second int,
    milli int,
    event_time timestamp,
    PRIMARY KEY ((id, category), year, month, day, hour)
) WITH CLUSTERING ORDER BY (year desc, month DESC, day DESC);