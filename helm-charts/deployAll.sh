#!/usr/bin/env bash

function usage {
    echo "$0 {dev|demo|prod}"
}

if [[ -z "$1" ]]; then
    usage
    exit 1
fi

ENV=$1

CUR_EVENT_LOG_TAG=201905120723-dev

CUR_EVENT_LOOKUP_TAG=201905120723-dev

CUR_EVENT_ENCODER_TAG=201905120707-dev

CUR_EVENT_DISPATCHER_TAG=201905081800-dev

CUR_EVENT_CHAINER_TAG=201905091800-dev

# CosmoDB
#./deployK8SService.sh $ENV event-log-service $CUR_EVENT_LOG_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-$ENV.yaml"
# Insta
./deployK8SService.sh $ENV event-log-service $CUR_EVENT_LOG_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-$ENV-insta.yaml"

# CosmoDB
#./deployK8SService.sh $ENV event-log-kafka-lookup $CUR_EVENT_LOOKUP_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-kafka-lookup-$ENV.yaml"
# Insta
./deployK8SService.sh $ENV event-log-kafka-lookup $CUR_EVENT_LOOKUP_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-kafka-lookup-$ENV-insta.yaml"

./deployK8SService.sh $ENV event-log-encoder $CUR_EVENT_ENCODER_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-encoder-$ENV.yaml"
./deployK8SService.sh $ENV event-log-dispatcher $CUR_EVENT_DISPATCHER_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-dispatcher-$ENV.yaml"
./deployK8SService.sh $ENV event-log-chainer $CUR_EVENT_CHAINER_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-chainer-$ENV.yaml"