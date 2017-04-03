#!/bin/bash

if [ $(ps x|grep '/bin/bash ./start.sh'|grep -v grep|wc -l) -ne 1 ]; then
    # just in case
    for pid in $(ps x|grep -E 'start.sh|java'|grep -v sandbox|grep -v grep|awk '{print $1}'); do
        kill -9 "$pid"
    done
    sleep 10

    cd /home/i6serverhost/init6
    ./start.sh &> /dev/null &
fi
