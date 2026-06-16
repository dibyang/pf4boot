#!/bin/sh
set -e
if command -v systemctl >/dev/null 2>&1; then
    systemctl daemon-reload || true
fi
rm -rf /opt/pf4boot/cross-plugin-jpa-sample
