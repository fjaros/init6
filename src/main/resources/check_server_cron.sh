#!/bin/bash

if [ $(ps x|grep '/bin/bash ./start.sh'|grep -v grep|wc -l) -ne 1 ]; then
    # just in case
    killall -9 start.sh java
    sleep 10

    cd /home/i6serverhost/init6
    ./start.sh &> /dev/null &
fi
