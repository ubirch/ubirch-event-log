#!/usr/bin/env bash

function usage {
    echo "$0 {dev|demo|prod}"
}

if [[ -z "$1" ]]; then
    usage
    exit 1
fi

ENV=$1

#CUR_TAG=116-fa46196
if [[ "dev" == "$ENV" ]]; then
    CUR_TAG=latest
else
#    CUR_TAG=stable
    CUR_TAG=latest

fi

#if [[ "dev" == "$ENV" ]]; then
#     Insta

./deployK8SService.sh $ENV event-log-service $CUR_EVENT_LOG_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-service-$ENV-insta.yaml"
./deployK8SService.sh $ENV event-log-kafka-lookup $CUR_EVENT_LOOKUP_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-kafka-lookup-$ENV-insta.yaml"

#else
#     CosmoDB
#    ./deployK8SService.sh $ENV event-log-service $CUR_EVENT_LOG_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-service-log-$ENV.yaml"
#    ./deployK8SService.sh $ENV event-log-kafka-lookup $CUR_EVENT_LOOKUP_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-kafka-lookup-$ENV.yaml"
#fi

./deployK8SService.sh $ENV event-log-encoder $CUR_EVENT_ENCODER_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-encoder-$ENV.yaml"
./deployK8SService.sh $ENV event-log-dispatcher $CUR_EVENT_DISPATCHER_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-dispatcher-$ENV.yaml"
./deployK8SService.sh $ENV event-log-chainer $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-chainer-$ENV.yaml"
./deployK8SService.sh $ENV event-log-chainer-master $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-chainer-master-$ENV.yaml"
