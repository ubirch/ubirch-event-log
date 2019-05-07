#!/usr/bin/env bash

function usage {
    echo "$0 {dev|demo|prod}"
}

if [[ -z "$1" ]]; then
    usage
    exit 1
fi

ENV=$1

CUREVENTLOGTAG=201905052200-dev

CUREVENTLOOKUPTAG=201905052015-dev

CUREVENTADAPTERTAG=201905052213-dev

CUREVENTDISPATCHERTAG=201905071422-dev

CUREVENTCHAINERTAG=201905071858-dev

./deployK8SService.sh $ENV event-log-service $CUREVENTLOGTAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-$ENV.yaml"
./deployK8SService.sh $ENV event-log-kafka-lookup $CUREVENTLOOKUPTAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-kafka-lookup-$ENV.yaml"
./deployK8SService.sh $ENV ubirch-protocol-adapter $CUREVENTADAPTERTAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-ubirch-protocol-adapter-$ENV.yaml"
./deployK8SService.sh $ENV event-log-dispatcher $CUREVENTDISPATCHERTAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-dispatcher-$ENV.yaml"
./deployK8SService.sh $ENV event-log-chainer $CUREVENTCHAINERTAG "~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-chainer-$ENV.yaml"