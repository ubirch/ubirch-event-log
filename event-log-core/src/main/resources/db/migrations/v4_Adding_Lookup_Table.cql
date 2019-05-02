/* Cassandra migration for keyspace event_log.
   Version 4 - 2019-04-29T18:10:04.850187+00:00

   Adding Lookup Table */

drop table if exists lookups;

create table if not exists lookups (
    key text,
    value text,
    name text,
    PRIMARY KEY (value)
);