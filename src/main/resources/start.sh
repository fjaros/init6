#!/bin/bash
#
# -- init6 Server Start + Drop Script --
#
# Created by filip on 1/28/16.
#

# --------------------
# CONFIG VARS DEFAULTS
# --------------------

# if set to true, will block and log to console.
debug=false

# update jar. if this file is in the folder, it will be used on next restart
update_jar=init6.jar.update

config=init6.conf

# in minutes
min_wait=120
max_wait=180

# in seconds
min_between_drops=30
max_between_drops=45

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
        --config)
        config="$2"
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
        --min_between_drops)
        min_between_drops="$2"
        shift
        ;;
        --max_between_drops)
        max_between_drops="$2"
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
    echo "Script received terminate. Killing init6."
    if [ -n "$pid" ] && [ -e "/proc/$pid" ]; then
        kill "$pid"
        wait "$pid"
    fi
    exit
}

# Randomize wait time
function random_range() {
    local result=$1
    local min="$2"
    local max="$3"

    let "_result = RANDOM % (max - min + 1) + min"
    eval $result="$_result"
}

# check java version
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "Detected java version $java_version"
if [[ ! "$java_version" > 1.8.* ]]; then
    echo "init6 requires java 1.8+ . Exiting."
    exit
fi

java_run=" \
    -XX:+HeapDumpOnOutOfMemoryError -XX:+DisableExplicitGC \
    -Xms128m -Xmx4g \
    -Dconfig=$config \
    -cp lib/*:init6.jar \
    com.init6.init6"

while :; do
    if [ -n "$pid" ]; then
        echo "PID set to $pid"
    else
        if [ -e "$update_jar" ]; then
            echo "Found $update_jar. Rewriting init6.jar..."
            mv "$update_jar" init6.jar
        fi
        echo "Starting init6..."

        log_file="init6_$(date +'%s%N'|cut -b1-13).log"
        if [ "$debug" = true ]; then
            java $java_run
        else
            java $java_run &> "$log_file" &
        fi
        pid=$!
    fi

    random_range wait_time "$min_wait" "$max_wait"

    echo "Waiting $wait_time minutes before next drop"
    let "wait_time *= 60"

    waited_time=0
    while [ "$waited_time" -lt "$wait_time" ]; do
        if ps -p "$pid" > /dev/null; then
            sleep "$check_proc_interval"
            let "waited_time += check_proc_interval"

            if [ "$waited_time" -ge "$wait_time" ]; then
                echo "Wait time elapsed. Dropping init6..."
                kill "$pid"
                wait "$pid"
                pid=""

                random_range between_drops "$min_between_drops" "$max_between_drops"

                echo "Killed init6. Sleeping $between_drops seconds between drops."
                sleep "$between_drops"
            fi
        else
            if [ "$restart_if_killed" = true ]; then
                echo "init6 dropped outside of drop script. Restarting..."
                break
            else
                echo "init6 dropped outside of drop script."
                exit
            fi
        fi
    done
done
