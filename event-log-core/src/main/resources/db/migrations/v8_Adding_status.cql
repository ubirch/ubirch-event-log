/* Cassandra migration for keyspace event_log.
   Version 8 - 2021-03-18T08:29:29.223675+00:00

   Adding status */

ALTER TABLE events ADD status text;
