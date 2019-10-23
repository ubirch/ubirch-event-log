/* Cassandra migration for keyspace event_log.
   Version 7 - 2019-10-15T10:44:20.851708+00:00

   Materialized Views Events */


use event_log;

drop MATERIALIZED VIEW IF exists events_by_cat;

CREATE MATERIALIZED VIEW events_by_cat AS
SELECT *
FROM events
WHERE category IS NOT null
  and year is not null
  and month is not null
  and day is not null
  and hour is not null
  and minute is not null
  and second is not null
  and milli is not null
  and id is not null
    PRIMARY KEY ((category, year, month), day, hour, minute, second, milli, id)
    WITH CLUSTERING ORDER BY (day DESC, hour DESC, minute DESC, second DESC, milli DESC, id ASC);
