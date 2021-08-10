#!/usr/bin/env bash

l_COUNT=0
while [[ "${l_COUNT}" != 3 ]]; do
  echo "{\"hostname\":\"$(hostname)-${l_COUNT:-0}\"}"
  sleep .25
  l_COUNT=$((l_COUNT+1))
done