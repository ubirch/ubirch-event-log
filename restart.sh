#!/bin/bash

if [ -z "$1" -o -z "$2" -o -z "$3" ]; then
  echo "help!"
  exit 1
fi

environment=$1
namespace=$2
filter=$3
force=$4

k8sc.sh "${environment}" -n "${namespace}" get pods \
  |cut -f1 -d " " \
  |sed 1d \
  |grep "${filter}" \
  |tac \
  |while read line; do \
    command="k8sc.sh ${environment} -n ${namespace} delete pod ${line}"
    if [ "run" = "${force}" ] ; then
      ${command}
    else
      echo ${command}
    fi
    #sleep 0.5;
  done