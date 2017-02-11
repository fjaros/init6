#!/bin/bash

mvn clean package

scp -P22022 target/init6.jar vilenet@dal.wserv.org:/home/vilenet/init6-test
scp -P22022 src/main/resources/akka.conf vilenet@dal.wserv.org:/home/vilenet/init6-test
ssh -p22022 -f vilenet@dal.wserv.org "./kill_test.sh"
ssh -p22022 -f vilenet@dal.wserv.org "cd init6-test; ./start.sh --debug true --test"

scp -P22022 target/init6.jar vilenet@sea.wserv.org:/home/vilenet/init6-test
scp -P22022 src/main/resources/akka.conf vilenet@sea.wserv.org:/home/vilenet/init6-test
ssh -p22022 -f vilenet@sea.wserv.org "./kill_test.sh"
ssh -p22022 -f vilenet@sea.wserv.org "cd init6-test; ./start.sh --debug true --test"

read x

ssh -p22022 -f vilenet@sea.wserv.org "./kill_test.sh"
ssh -p22022 -f vilenet@dal.wserv.org "./kill_test.sh"
