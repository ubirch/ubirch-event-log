#!/bin/bash

set +e

function usage {
    echo "$0 {dev|demo|prod} {servicename} {docker image tag} {values file}"
    echo "e.g. $0 dev event-log-service 201905052200-dev ~/workspace/ubirch/_k8s/ubirch-kubernetes/19_event-log/values-event-log-dev.yaml"
    echo "services: event-log-service / event-log-sdk / event-log-kafka-lookup / ubirch-protocol-adapter / event-log-dispatcher"
}

if [[ -z "$1" ]]; then
    usage
    exit 1
fi

if [[ -z "$2" ]]; then
    usage
    exit 1
fi

if [[ -z "$3" ]]; then
    usage
    exit 1
fi

if [[ -z "$4" ]]; then
    usage
    exit 1
fi

ENV=$1
REALENV=${ENV}
SERVICENAME=$2
IMAGETAG=$3
VALUES=$4

if [ "devbeta" == "$ENV" ]; then
  REALENV="dev"
fi

if [[ -d /Volumes/Keybase/team ]];
then
    KEYBASE_BASE="/Volumes/Keybase/team"
else
    KEYBASE_BASE="/keybase/team"
fi

if [[ -f ${KEYBASE_BASE}/ubirchdevops/bin/helm.sh ]];
then
    HELM=${KEYBASE_BASE}/ubirchdevops/bin/helm.sh
else
    HELM=${KEYBASE_BASE}/ubirch_developer/devkube/bin/helm.sh
fi

$HELM $ENV delete $SERVICENAME --purge
$HELM $ENV install \
    $SERVICENAME --namespace core-$REALENV \
    --name $SERVICENAME --debug \
    --set image.tag=$IMAGETAG \
    -f $VALUES
