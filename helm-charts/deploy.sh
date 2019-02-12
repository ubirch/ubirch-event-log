#!/bin/bash

/keybase/team/ubirchdevops/bin/helm.sh dev delete event-log-service --purge
/keybase/team/ubirchdevops/bin/helm.sh dev install event-log-service --namespace core-dev --name event-log-service --debug -f event-log-service/values-dev.yaml \
   --set Values.build.number=20190212-dev
