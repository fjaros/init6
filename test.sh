#!/bin/bash

mvn clean package

scp target/init6.jar vilenet@mtl.wserv.org:/home/vilenet/init6-test
ssh -f vilenet@mtl.wserv.org "cd init6-test; ./start.sh --debug true --test"

scp target/init6.jar vilenet@sea.wserv.org:/home/vilenet/init6-test
ssh -f vilenet@sea.wserv.org "cd init6-test; ./start.sh --debug true --test"

read x

ssh -f vilenet@sea.wserv.org "./kill_test.sh"
ssh -f vilenet@mtl.wserv.org "./kill_test.sh"
