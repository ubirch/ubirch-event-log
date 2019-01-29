#!/bin/bash -x

baseline=`echo "$1" | awk '{print tolower($0)}'`

cd event-log-core/src/main/resources

if [ "baseline" == "${baseline}" ]; then
    echo "creating baseline"
    cassandra-migrate -H 127.0.0.1 -p 9042 baseline
fi

echo "migrate DB"
cassandra-migrate -H 127.0.0.1 -p 9042 migrate
