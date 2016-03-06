#!/bin/bash
#
# -- ViLeNet Server Start + Drop Script --
#
# Created by filip on 1/28/16.
#

# -----------
# CONFIG VARS
# -----------

log_file=vilenet.log

# if set to true, will block and log to console.
debug=false

akka_host=127.0.0.1
akka_port=2552
akka_nodes=(127.0.0.1:2552 127.0.0.1:2553)

min_wait=120
max_wait=300
between_drops=10

restart_if_killed=false
check_proc_interval=5

# ------
# SCRIPT
# ------

# override echo to prepend timestamp
function echo() {
    date +"[%Y-%m-%d %H:%M:%S] $*"
}


# trap interrupt and terminate signals to kill the spawned process
trap shutdown_hook INT TERM

function shutdown_hook() {
    echo "Script received terminate. Killing ViLeNet."
    if [ -n "$pid" ] && [ -e "/proc/$pid" ]; then
        kill "$pid"
        wait "$pid"
    fi
    exit
}

# check java version
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "Detected java version $java_version"
if [[ ! "$java_version" > 1.8.* ]]; then
    echo "ViLeNet requires java 1.8+ . Exiting."
    exit
fi

# build the java run string
for i in "${!akka_nodes[@]}"; do
    node_string="$node_string -Dakka.cluster.seed-nodes.$i=akka.tcp://ViLeNet@${akka_nodes[$i]}"
done

java_run=" \
    -Dakka.remote.netty.tcp.hostname=$akka_host \
    -Dakka.remote.netty.tcp.port=$akka_port \
    $node_string \
    -jar vilenet-0.1.jar \
    akka.conf \
    vilenet.conf"


while :; do
    echo "Starting ViLeNet..."
    if [ "$debug" = true ]; then
        java $java_run
    else
        java $java_run > $log_file &
    fi

    pid=$!
    waited_time=0
    let "wait_time = (RANDOM % (max_wait - min_wait)) + min_wait"

    echo "Waiting $wait_time minutes before next drop"
    let "wait_time *= 60"

    while [ "$waited_time" -lt "$wait_time" ]; do
        if [ -e "/proc/$pid" ]; then
            sleep "$check_proc_interval"
            let "waited_time += check_proc_interval"

            if [ "$waited_time" -ge "$wait_time" ]; then
                echo "Wait time elapsed. Dropping ViLeNet..."
                kill "$pid"
                wait "$pid"
                pid=""

                echo "Killed ViLeNet. Sleeping $between_drops seconds between drops."
                sleep "$between_drops"
            fi
        else
            if [ "$restart_if_killed" = true ]; then
                echo "ViLeNet dropped outside of drop script. Restarting..."
                break
            else
                echo "ViLeNet dropped outside of drop script."
                exit
            fi
        fi
    done
done
