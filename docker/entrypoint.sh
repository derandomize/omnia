#!/bin/bash

run_migrator() {
    java -jar /app/migrator.jar "$@"
}

if [ $# -eq 0 ]; then
    run_migrator --help
    exit 0
fi

if [ "$1" = "daemon" ]; then
    shift

    /app/migrator-daemon.sh -e "/app/entrypoint.sh" -i "$DAEMON_INTERVAL" -c "$CONFIG_PATH" "$@"
else
    run_migrator "$@"
fi
