/* Cassandra migration for keyspace event_log.
   Version 1 - 2018-12-03T14:36:07.558777+00:00

   Create_Event */

drop table if exists events;

create table if not exists events (
    id uuid,
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
    created timestamp,
    updated timestamp,
    PRIMARY KEY ((id, category), year, month, day, hour)
) WITH CLUSTERING ORDER BY (year desc, month DESC, day DESC);