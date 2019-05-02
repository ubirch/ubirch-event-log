/* Cassandra migration for keyspace event_log.
   Version 3 - 2019-04-09T21:28:09.519952+00:00

   Adding nonce */


ALTER TABLE events ADD nonce text;
