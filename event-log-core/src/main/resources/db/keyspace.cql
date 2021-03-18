drop  KEYSPACE event_log;
CREATE KEYSPACE event_log WITH replication = {'class': 'SimpleStrategy','replication_factor': '2'};

CREATE KEYSPACE event_log WITH replication = {'class': 'NetworkTopologyStrategy','AZURE_WEST_EUROPE_DC1': '3'};

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
    nonce text,
    status text,
    PRIMARY KEY ((id, category), year, month, day, hour, minute, second, milli)
) WITH CLUSTERING ORDER BY (year DESC, month DESC, day DESC, hour DESC, minute DESC, second DESC, milli DESC);
