#!/bin/sh
set -e
if command -v systemctl >/dev/null 2>&1; then
    systemctl stop pf4boot-cross-plugin-jpa-sample || true
    systemctl disable pf4boot-cross-plugin-jpa-sample || true
fi
