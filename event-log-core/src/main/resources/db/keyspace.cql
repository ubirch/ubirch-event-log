drop  KEYSPACE event_log;
CREATE KEYSPACE event_log WITH replication = {'class': 'SimpleStrategy','replication_factor': '2'};

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
    PRIMARY KEY ((id, category), year, month, day, hour)
) WITH CLUSTERING ORDER BY (year desc, month DESC, day DESC);