#!/bin/bash

# Configuration
EXECUTABLE="./migrator"  # Path to your executable
CONFIG_FILE=""           # Optional config file path
INTERVAL=3600           # Interval in seconds (default: 1 hour)
PID_FILE="/var/run/migration-daemon.pid"
LOG_FILE="/var/log/migration-daemon.log"
MAX_LOG_SIZE=10485760   # 10MB in bytes

log_message() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

rotate_log() {
    if [ -f "$LOG_FILE" ] && [ "$(stat -f%z "$LOG_FILE" 2>/dev/null || stat -c%s "$LOG_FILE" 2>/dev/null || echo 0)" -gt $MAX_LOG_SIZE ]; then
        mv "$LOG_FILE" "${LOG_FILE}.old"
        touch "$LOG_FILE"
        log_message "Log rotated"
    fi
}

cleanup() {
    log_message "Migration daemon stopping..."
    rm -f "$PID_FILE"
    exit 0
}

is_running() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid=$(cat "$PID_FILE")
        if ps -p "$pid" > /dev/null 2>&1; then
            return 0
        else
            rm -f "$PID_FILE"
            return 1
        fi
    fi
    return 1
}

start_daemon() {
    if is_running; then
        echo "Migration daemon is already running (PID: $(cat "$PID_FILE"))"
        exit 1
    fi

    echo "Starting migration daemon..."

    # Create PID file
    echo $$ > "$PID_FILE"

    # Set up signal handlers
    trap cleanup SIGTERM SIGINT

    log_message "Migration daemon started (PID: $$, Interval: ${INTERVAL}s)"

    while true; do
        rotate_log

        log_message "Starting migrate-all command..."

        # Build command with optional config
        if [ -n "$CONFIG_FILE" ]; then
            CMD="$EXECUTABLE --config $CONFIG_FILE migrate-all"
        else
            CMD="$EXECUTABLE migrate-all"
        fi

        # Execute the migration command
        if $CMD >> "$LOG_FILE" 2>&1; then
            log_message "migrate-all completed successfully"
        else
            log_message "migrate-all failed with exit code $?"
        fi

        log_message "Sleeping for $INTERVAL seconds..."
        sleep "$INTERVAL"
    done
}

stop_daemon() {
    if ! is_running; then
        echo "Migration daemon is not running"
        return 1
    fi

    local pid
    pid=$(cat "$PID_FILE")

    echo "Stopping migration daemon (PID: $pid)..."

    kill "$pid"

    # Wait for process to stop
    local count=0
    while [ $count -lt 10 ] && ps -p "$pid" > /dev/null 2>&1; do
        sleep 1
        count=$((count + 1))
    done

    if ps -p "$pid" > /dev/null 2>&1; then
        echo "Force killing daemon..."
        kill -9 "$pid"
    fi

    rm -f "$PID_FILE"
    echo "Migration daemon stopped"
}

status_daemon() {
    if is_running; then
        echo "Migration daemon is running (PID: $(cat "$PID_FILE"))"
        return 0
    else
        echo "Migration daemon is not running"
        return 1
    fi
}

usage() {
    echo "Usage: $0 [options] {start|stop|restart|status}"
    echo ""
    echo "Options:"
    echo "  -e, --executable PATH    Path to migrator executable (default: ./migrator)"
    echo "  -c, --config PATH        Config file path"
    echo "  -i, --interval SECONDS   Interval between runs (default: 3600)"
    echo "  -p, --pid-file PATH      PID file path (default: /var/run/migration-daemon.pid)"
    echo "  -l, --log-file PATH      Log file path (default: /var/log/migration-daemon.log)"
    echo "  -h, --help              Show this help"
    echo ""
    echo "Commands:"
    echo "  start     Start the daemon"
    echo "  stop      Stop the daemon"
    echo "  restart   Restart the daemon"
    echo "  status    Show daemon status"
}

while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--executable)
            EXECUTABLE="$2"
            shift 2
            ;;
        -c|--config)
            CONFIG_FILE="$2"
            shift 2
            ;;
        -i|--interval)
            INTERVAL="$2"
            shift 2
            ;;
        -p|--pid-file)
            PID_FILE="$2"
            shift 2
            ;;
        -l|--log-file)
            LOG_FILE="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        start|stop|restart|status)
            COMMAND="$1"
            shift
            ;;
        *)
            echo "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Check if executable exists
if [ ! -f "$EXECUTABLE" ] && [ ! -x "$(command -v "$EXECUTABLE")" ]; then
    echo "Error: Executable '$EXECUTABLE' not found or not executable"
    exit 1
fi

case "${COMMAND:-}" in
    start)
        start_daemon
        ;;
    stop)
        stop_daemon
        ;;
    restart)
        stop_daemon
        sleep 2
        start_daemon
        ;;
    status)
        status_daemon
        ;;
    *)
        echo "Error: No command specified"
        usage
        exit 1
        ;;
esac
