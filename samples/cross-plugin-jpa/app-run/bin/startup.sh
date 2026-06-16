#!/usr/bin/env sh
set -eu
APP_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$APP_DIR"
LOG_DIR="${LOG_DIR:-logs}"
mkdir -p "$LOG_DIR"
exec java ${JAVA_OPTS:-} -Dsample.logging.dir="$LOG_DIR" -cp "lib/*" net.xdob.sample.host.CrossPluginJpaSampleHost --spring.config.location=config/application.yml
