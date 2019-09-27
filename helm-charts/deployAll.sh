#!/usr/bin/env bash

function usage {
    echo "$0 {dev|devbeta|demo|prod}"
}

if [[ -z "$1" ]]; then
    usage
    exit 1
fi

ENV=$1
REALENV=${ENV}

#CUR_TAG=116-fa46196
if [[ "dev" == "$ENV" ]]; then
    CUR_TAG=latest
elif [[ "devbeta" == "$ENV" ]]; then
    REALENV="dev"
    CUR_TAG=latest
else
#    $CUR_TAG=stable
    CUR_TAG=latest
fi

#if [[ "dev" == "$ENV" ]]; then
#     Insta
echo ./deployK8SService.sh $ENV event-log-service $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-service-$REALENV-insta.yaml"
./deployK8SService.sh $ENV event-log-service $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-service-$REALENV-insta.yaml"
./deployK8SService.sh $ENV event-log-kafka-lookup $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-kafka-lookup-$REALENV-insta.yaml"

#else
#     CosmoDB
#    ./deployK8SService.sh $ENV event-log-service $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-service-log-$REALENV.yaml"
#    ./deployK8SService.sh $ENV event-log-kafka-lookup $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-kafka-lookup-$REALENV.yaml"
#fi

./deployK8SService.sh $ENV event-log-encoder $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-encoder-$REALENV.yaml"
./deployK8SService.sh $ENV event-log-dispatcher $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-dispatcher-$REALENV.yaml"
./deployK8SService.sh $ENV event-log-chainer $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-chainer-$REALENV.yaml"
./deployK8SService.sh $ENV event-log-chainer-master $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-chainer-master-$REALENV.yaml"
./deployK8SService.sh $ENV event-log-discovery-creator $CUR_TAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-discovery-creator-$REALENV.yaml"
