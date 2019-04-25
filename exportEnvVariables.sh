#!/bin/bash -x

#Script Helper to add EventLog Environment Keys to current session.
#Usage:
#source exportEnvVariables.sh

export EVTL_CS_KEYSPACE=event_log
export EVTL_CS_PREPSTM_CACHE=1000
export EVTL_CS_NODES=192.168.1.108:9042
export EVTL_CS_CONSISTENCY_LEVEL=LOCAL_ONE
export EVTL_CS_SERIAL_CONSISTENCY_LEVEL=SERIAL
export EVTL_CS_WITH_SSL=false
export EVTL_CS_USERNAME=
export EVTL_CS_PASSWORD=
export EVTL_KFC_NODES=localhost:9092
export EVTL_KFC_TOPIC=com.ubirch.eventlog
export EVTL_KFC_GROUP=event_log_group
export EVTL_KFP_NODES=localhost:9092
export EVTL_KFC_ERROR_TOPIC=com.ubirch.eventlog.error

