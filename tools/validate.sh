#!/usr/bin/env bash
# Checks the face with Google's tools (clone and build them first, see README):
# the XML against the WFF v4 schema, then the built APK against the memory limits.
#
# Usage: tools/validate.sh [path/to/google-watchface-clone]
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
tools="${1:-$repo/../watchface-tools/watchface}"
validator="$tools/third_party/wff/specification/validator/build/libs/wff-validator.jar"
footprint="$tools/play-validations/memory-footprint/build/libs/memory-footprint.jar"
face="$repo/watchface/src/main/res/raw/watchface.xml"
apk="$repo/watchface/build/outputs/apk/debug/watchface-debug.apk"

for jar in "$validator" "$footprint"; do
    if [ ! -f "$jar" ]; then
        echo "Missing $jar. Build it first, see the README." >&2
        exit 2
    fi
done
if [ ! -f "$apk" ]; then
    echo "Missing $apk. Run ./gradlew :watchface:assembleDebug first." >&2
    exit 2
fi

# Both tools can exit 0 after a failure, so read what they say too.
output="$(java -jar "$validator" 4 "$face" 2>&1)" || { echo "$output"; exit 1; }
echo "$output"
case "$output" in
    *PASSED*) ;;
    *) exit 1 ;;
esac

output="$(java -jar "$footprint" --watch-face "$apk" --schema-version 4 \
    --ambient-limit-mb 10 --active-limit-mb 100 \
    --apply-v1-offload-limitations --estimate-optimization 2>&1)" || { echo "$output"; exit 1; }
echo "$output"
case "$output" in
    *"has passed"*) ;;
    *) exit 1 ;;
esac
