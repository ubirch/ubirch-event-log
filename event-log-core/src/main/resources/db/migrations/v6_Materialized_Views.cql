/* Cassandra migration for keyspace event_log.
   Version 6 - 2019-10-09T11:29:51.938035+00:00

   Materialized_Views */

use event_log;

drop MATERIALIZED VIEW IF exists events_by_cat;

CREATE MATERIALIZED VIEW events_by_cat AS 
SELECT * FROM events 
WHERE 
category IS NOT null and 
year is not null and 
month is not null and
day is not null and
hour is not null and
id is not null
PRIMARY KEY ((category, year, month), day, hour, id);


