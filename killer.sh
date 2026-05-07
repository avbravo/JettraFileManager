#!/usr/bin/env bash
# ----------------------------------------------------------------------
# killer.sh – Stop all Jettra JavaFX UI processes and clean up resources
# ----------------------------------------------------------------------
# Usage:
#   ./killer.sh               # stop all Jettra UI processes
#   ./killer.sh -f            # force kill (SIGKILL) if normal shutdown fails
# ----------------------------------------------------------------------
# This script should reside at the same directory level as buildjavafx.sh.
# ----------------------------------------------------------------------

# Function: display a header banner
banner() {
    echo "============================================================"
    echo " Jettra JavaFX Interface Terminator"
    echo "============================================================"
}

# Function: attempt graceful shutdown of a JavaFX process
#   Sends SIGTERM, then waits for the process to exit.
#   If it does not exit within TIMEOUT seconds, falls back to SIGKILL
graceful_stop() {
    local pid=$1
    local timeout=$2
    echo "[INFO] Sending SIGTERM to PID $pid ..."
    kill -TERM "$pid" 2>/dev/null

    local elapsed=0
    while kill -0 "$pid" 2>/dev/null && [ $elapsed -lt $timeout ]; do
        sleep 1
        ((elapsed++))
    done

    if kill -0 "$pid" 2>/dev/null; then
        echo "[WARN] PID $pid did not terminate after $timeout seconds."
        return 1
    else
        echo "[OK] PID $pid terminated gracefully."
        return 0
    fi
}

# Function: forcefully kill a process (SIGKILL)
force_kill() {
    local pid=$1
    echo "[INFO] Sending SIGKILL to PID $pid ..."
    kill -KILL "$pid" 2>/dev/null
    sleep 1
    if kill -0 "$pid" 2>/dev/null; then
        echo "[ERROR] Unable to kill PID $pid."
    else
        echo "[OK] PID $pid killed."
    fi
}

# ----------------------------------------------------------------------
# Main script
# ----------------------------------------------------------------------
banner

# Define the JavaFX main class names used by Jettra components.
# Adjust these if you add new UI modules.
JAVA_FX_CLASSES=(
    "io.jettra.fs.fx.JettraFileManagerFX"
    # Add additional UI entry points here if needed
)

# Build a greppable pattern from the class names.
PATTERN=$(printf "%s|" "${JAVA_FX_CLASSES[@]}")
PATTERN=${PATTERN%|}

# Find Java processes that contain any of the target class names.
echo "[INFO] Scanning for running Jettra JavaFX processes..."
JAVA_PIDS=$(ps -eo pid,cmd | grep java | grep -E "$PATTERN" | grep -v grep | awk '{print $1}')

if [ -z "$JAVA_PIDS" ]; then
    echo "[INFO] No Jettra JavaFX processes found."
    exit 0
fi

# Determine if user requested force kill.
FORCE_KILL=false
if [[ "$1" == "-f" ]]; then
    FORCE_KILL=true
    echo "[INFO] Force‑kill mode enabled."
fi

# Iterate over each PID and attempt shutdown.
for pid in $JAVA_PIDS; do
    echo "------------------------------------------------------------"
    echo "[TARGET] PID $pid"
    if graceful_stop "$pid" 15; then
        continue
    fi
    if $FORCE_KILL; then
        force_kill "$pid"
    else
        echo "[ACTION] Run the script with '-f' to force‑kill remaining processes."
    fi
done

# Optional cleanup of leftover temporary directories used by Jettra.
TEMP_BASE="${PWD}/.jettra_temp"
if [ -d "$TEMP_BASE" ]; then
    echo "[CLEANUP] Removing leftover temporary chunk directories..."
    rm -rf "$TEMP_BASE"
    echo "[CLEANUP] Done."
fi

echo "[DONE] All Jettra JavaFX UI instances have been stopped."
