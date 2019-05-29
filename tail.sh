#!/bin/bash
set -eux
source env.sh
awslogs get -w datomic-ions-demo
