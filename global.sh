#!/bin/bash

#./build.sh

#scp -P22022 target/init6.jar i6serverhost@216.244.82.98:/home/i6serverhost/init6.jar.new
#scp -P22022 src/main/resources/akka.conf i6serverhost@sea.wserv.org:/home/i6serverhost/init6/akka.conf
#scp -P22022 target/init6.jar i6serverhost@205.234.159.242:/home/i6serverhost/init6.jar.new
#scp -P22022 src/main/resources/akka.conf i6serverhost@chi.wserv.org:/home/i6serverhost/init6/akka.conf
#scp -P22022 target/init6.jar i6serverhost@dal.wserv.org:/home/i6serverhost/init6.jar.new
#scp -P22022 src/main/resources/akka.conf i6serverhost@dal.wserv.org:/home/i6serverhost/init6/akka.conf
#scp -P22022 target/init6.jar i6serverhost@chat.wserv.org:/home/i6serverhost/init6.jar.new
#scp -P22022 src/main/resources/akka.conf i6serverhost@chat.wserv.org:/home/i6serverhost/init6/akka.conf
#scp -P22022 target/init6.jar i6serverhost@50.2.212.132:/home/i6serverhost/init6.jar.new

ssh -p22022 -f i6serverhost@216.244.82.98 "crontab -r"
ssh -p22022 -f i6serverhost@dal.wserv.org "crontab -r"
ssh -p22022 -f i6serverhost@chat.wserv.org "crontab -r"
ssh -p22022 -f i6serverhost@205.234.159.242 "crontab -r"

sleep 1

ssh -p22022 -f i6serverhost@216.244.82.98 "./k.sh"
ssh -p22022 -f i6serverhost@dal.wserv.org "./k.sh"
ssh -p22022 -f i6serverhost@chat.wserv.org "./k.sh"
ssh -p22022 -f i6serverhost@205.234.159.242 "./k.sh"

#ssh -p22022 -f i6serverhost@50.2.212.132 "./k.sh"

sleep 10

for i in $(seq 1 1 3); do
    if [ $i -eq 1 ]; then
        ssh -p22022 -f i6serverhost@216.244.82.98 "cp init6.jar.new init6/init6.jar; cd init6; ./start.sh &> /dev/null"
    elif [ $i -eq 2 ]; then
        ssh -p22022 -f i6serverhost@205.234.159.242 "cp init6.jar.new init6/init6.jar; cd init6; ./start.sh &> /dev/null"
    elif [ $i -eq 3 ]; then
        ssh -p22022 -f i6serverhost@dal.wserv.org "cp init6.jar.new init6/init6.jar; cd init6; ./start.sh &> /dev/null"
    fi
    sleep 8
done

ssh -p22022 -f i6serverhost@chat.wserv.org "cp init6.jar.new init6/init6.jar; cd init6; ./start.sh &> /dev/null"
#ssh -p22022 -f i6serverhost@chat.wserv.org "cp init6.jar.new init6-sandbox/init6.jar; cp init6/akka.conf init6-sandbox/akka.conf; cd init6-sandbox; ./start_sandbox.sh &> /dev/null"

sleep 10

ssh -p22022 -f i6serverhost@chat.wserv.org "echo \"*/5 * * * * /home/i6serverhost/check_server_cron.sh\"|crontab -"
ssh -p22022 -f i6serverhost@205.234.159.242 "echo \"*/5 * * * * /home/i6serverhost/check_server_cron.sh\"|crontab -"
ssh -p22022 -f i6serverhost@216.244.82.98 "echo \"*/5 * * * * /home/i6serverhost/check_server_cron.sh\"|crontab -"
ssh -p22022 -f i6serverhost@dal.wserv.org "echo \"*/5 * * * * /home/i6serverhost/check_server_cron.sh\"|crontab -"
