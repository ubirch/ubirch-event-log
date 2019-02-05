#!/bin/bash -x

#Script Helper to remove EventLog Environment Keys from current session.
#Usage:
#source deleteEnvVariables.sh

export -n  EVTL_CS_KEYSPACE
export -n  EVTL_CS_PREPSTM_CACHE
export -n  EVTL_CS_NODES
export -n  EVTL_CS_USERNAME
export -n  EVTL_CS_PASSWORD
export -n  EVTL_KFC_NODES
export -n  EVTL_KFC_TOPIC
export -n  EVTL_KFC_GROUP
export -n  EVTL_KFP_NODES
export -n  EVTL_KFC_ERROR_TOPIC

