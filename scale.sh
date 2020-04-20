#!/bin/bash

if [ -z "$1" -o -z "$2" -o -z "$3" -o -z "$4" ]; then
  echo "help!"
  exit 1
fi

environment=$1
namespace=$2
filter=$3
scale=$4
force=$5

k8sc.sh "${environment}" -n "${namespace}" get deployment \
  |cut -f1 -d " " \
  |sed 1d \
  |grep "${filter}" \
  |tac \
  |while read line; do \
    command="k8sc.sh ${environment} scale -n ${namespace} deployment.v1.apps/${line} --replicas=${scale}"
    if [ "run" = "${force}" ] ; then
      ${command}
    else
      echo ${command}
    fi
    #sleep 0.5;
  done
