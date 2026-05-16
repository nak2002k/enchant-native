#!/bin/sh
GRADLE=$(which gradle 2>/dev/null || echo "$(dirname "$0")/gradlew")
exec "$GRADLE" "$@"
