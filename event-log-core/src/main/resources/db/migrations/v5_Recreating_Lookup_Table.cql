/* Cassandra migration for keyspace event_log.
   Version 5 - 2019-05-03T22:49:16.734786+00:00

   Recreating Lookup Table */

drop table if exists lookups;

create table if not exists lookups (
    key text,
    value text,
    category text,
    PRIMARY KEY (value, category)
);