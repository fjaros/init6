#!/bin/bash
#
# -- ViLeNet Server Start + Drop Script --
#
# Created by filip on 1/28/16.
#

# --------------------
# CONFIG VARS DEFAULTS
# --------------------

# if set to true, will block and log to console.
debug=false

min_wait=120
max_wait=180
between_drops=10

restart_if_killed=true
check_proc_interval=5

# ---------------------
# CONFIG VARS OVERRIDES
# ---------------------
while [ $# -gt 1 ]; do
    key="$1"
    case $key in
        --debug)
        debug="$2"
        shift
        ;;
        --log_file)
        log_file="$2"
        shift
        ;;
        --min_wait)
        min_wait="$2"
        shift
        ;;
        --max_wait)
        max_wait="$2"
        shift
        ;;
        --between_drops)
        between_drops="$2"
        shift
        ;;
        --restart_if_killed)
        restart_if_killed="$2"
        shift
        ;;
        --check_proc_interval)
        check_proc_interval="$2"
        shift
        ;;
        --pid)
        pid="$2"
        shift
        ;;
        *)
        ;;
    esac
    shift
done

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

java_run=" \
    -XX:+HeapDumpOnOutOfMemoryError -XX:+DisableExplicitGC \
    -Xms128m -Xmx4g \
    -Dconfig=vilenet.conf \
    -cp lib/*:vilenet-0.1.jar \
    com.vilenet.ViLeNet"

while :; do
    if [ -n "$pid" ]; then
        echo "PID set to $pid"
    else
        echo "Starting ViLeNet..."

        log_file="vilenet_$(date +'%s%N'|cut -b1-13).log"
        if [ "$debug" = true ]; then
            java $java_run
        else
            java $java_run > "$log_file" &
        fi
        pid=$!
    fi

    if [ "$min_wait" -eq "$max_wait" ]; then
        wait_time="$min_wait"
    else
        let "wait_time = (RANDOM % (max_wait - min_wait)) + min_wait"
    fi

    echo "Waiting $wait_time minutes before next drop"
    let "wait_time *= 60"

    waited_time=0
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
