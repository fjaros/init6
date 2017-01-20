#!/bin/bash

mvn clean package
scp target/init6.jar vilenet@dal.wserv.org:/home/vilenet/init6-test
scp target/init6.jar vilenet@sea.wserv.org:/home/vilenet/init6-test
