#!/bin/bash -x

#Basic Commands Available
#-------------------------------------------------------------
#migrate: To start running migrations.
#baseline: To "mark" current state of the db as input.
#          This won't run existing migrations. They will be skipped.
#          If the DB is new, you should run migrate instead.
#status: To show the current status of the migrations table.
#reset:  To reset current state of the db. This drops the keyspace and reapplies the script.
#        Useful for development.
#
# If no parameter is provided, then the migrate command will be initiated.
#
# For more information:
# https://github.com/ubirch/ubirch-cassandra-eval#how-to-run-httpsgithubcomcobliteamcassandra-migrate

command=$(echo "$1" | awk '{print tolower($0)}')

cd event-log-core/src/main/resources

if [ -z "${command}" ]; then
    echo "running migrate"
    cassandra-migrate -H 127.0.0.1 -p 9042 migrate
else
    echo "running ${command}"
    cassandra-migrate -H 127.0.0.1 -p 9042 ${command}
fi