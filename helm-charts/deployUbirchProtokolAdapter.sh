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

/keybase/team/ubirchdevops/bin/helm.sh dev delete  ubirch-protocol-adapter --purge
/keybase/team/ubirchdevops/bin/helm.sh dev install \
    ubirch-protocol-adapter --namespace core-dev \
    --name ubirch-protocol-adapter --debug \
    -f $2 \
    --set image.tag=$1
