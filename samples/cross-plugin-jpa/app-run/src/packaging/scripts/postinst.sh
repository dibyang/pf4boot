#!/bin/sh
set -e
chown -R pf4boot:pf4boot /opt/pf4boot/cross-plugin-jpa-sample
chmod 0755 /opt/pf4boot/cross-plugin-jpa-sample/bin/startup.sh
mkdir -p /opt/pf4boot/cross-plugin-jpa-sample/logs
mkdir -p /opt/pf4boot/cross-plugin-jpa-sample/work/h2
chown -R pf4boot:pf4boot /opt/pf4boot/cross-plugin-jpa-sample/logs
chown -R pf4boot:pf4boot /opt/pf4boot/cross-plugin-jpa-sample/work
if command -v systemctl >/dev/null 2>&1; then
    systemctl daemon-reload || true
    systemctl enable pf4boot-cross-plugin-jpa-sample || true
    systemctl start pf4boot-cross-plugin-jpa-sample || true
fi
