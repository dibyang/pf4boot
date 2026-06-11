#!/usr/bin/env sh
set -eu
APP_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$APP_DIR"
exec java -cp "lib/*" net.xdob.sample.host.CrossPluginJpaSampleHost --spring.config.location=config/application.yml
