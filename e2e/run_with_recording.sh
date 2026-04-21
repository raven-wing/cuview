#!/usr/bin/env bash
# Wraps a Maestro flow run with adb screenrecord; pulls the resulting mp4 to
# e2e/recordings/$NAME.mp4 and dumps logcat + activity-top to
# e2e/recordings/logs/$NAME.{logcat,activity-top}.txt regardless of pass/fail.
# Logs are captured AFTER the flow so they cover the actual run window
# (including any Chrome crash, ANR, or app death).
#
# Usage: e2e/run_with_recording.sh <NAME> <CMD...>
#   e.g. e2e/run_with_recording.sh 03_reconfigure maestro test e2e/flows/03_reconfigure.yaml
set -u
NAME=$1
shift

DIR="$(dirname "$0")"
mkdir -p "$DIR/recordings/logs"

# Clear logcat ring buffer so the post-flow dump only contains this flow's events.
adb logcat -c 2>/dev/null || true

adb shell screenrecord --time-limit 180 "/sdcard/$NAME.mp4" &
SR_PID=$!

STATUS=0
"$@" || STATUS=$?

# SIGINT lets screenrecord finalize the mp4 cleanly.
adb shell pkill -2 screenrecord 2>/dev/null || true
sleep 2
adb pull "/sdcard/$NAME.mp4" "$DIR/recordings/$NAME.mp4" 2>/dev/null || true
adb shell rm -f "/sdcard/$NAME.mp4" 2>/dev/null || true

adb logcat -d -v time > "$DIR/recordings/logs/$NAME.logcat.txt" 2>/dev/null || true
adb shell dumpsys activity top > "$DIR/recordings/logs/$NAME.activity-top.txt" 2>/dev/null || true
adb shell dumpsys dropbox --print 2>/dev/null > "$DIR/recordings/logs/$NAME.dropbox.txt" || true

wait "$SR_PID" 2>/dev/null || true

exit "$STATUS"
