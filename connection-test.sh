#!/bin/bash

export DATOMIC_SYSTEM=ions-demo
export DATOMIC_REGION=eu-west-1
export DATOMIC_SOCKS_PORT=8182

curl -x socks5h://localhost:$DATOMIC_SOCKS_PORT http://entry.$DATOMIC_SYSTEM.$DATOMIC_REGION.datomic.net:8182/
