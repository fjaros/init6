#!/bin/bash

mvn clean package

scp -P22022 target/init6.jar i6serverhost@dal.wserv.org:/home/i6serverhost/init6-test
scp -P22022 src/main/resources/akka.conf i6serverhost@dal.wserv.org:/home/i6serverhost/init6-test
#ssh -p22022 -f i6serverhost@dal.wserv.org "./kill_test.sh"
#ssh -p22022 -f i6serverhost@dal.wserv.org "cd init6-test; ./start.sh --debug true --test"

scp -P22022 target/init6.jar i6serverhost@chi.wserv.org:/home/i6serverhost/init6-test
scp -P22022 src/main/resources/akka.conf i6serverhost@chi.wserv.org:/home/i6serverhost/init6-test
#ssh -p22022 -f i6serverhost@sea.wserv.org "./kill_test.sh"
#ssh -p22022 -f i6serverhost@sea.wserv.org "cd init6-test; ./start.sh --debug true --test"

#read x

#ssh -p22022 -f i6serverhost@sea.wserv.org "./kill_test.sh"
#ssh -p22022 -f i6serverhost@dal.wserv.org "./kill_test.sh"
