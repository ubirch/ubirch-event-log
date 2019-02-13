#!/bin/bash

function usage {
    echo "$0 {docker image tag} {values file}"
}

if [[ -z "$1" ]]; then
    usage
    exit 1
fi

if [[ -z "$2" ]]; then
    usage
    exit 1
fi

/keybase/team/ubirchdevops/bin/helm.sh dev delete event-log-service --purge
/keybase/team/ubirchdevops/bin/helm.sh dev install \
    event-log-service --namespace core-dev \
    --name event-log-service --debug \
    -f $2 \
    --set image.tag=$1
