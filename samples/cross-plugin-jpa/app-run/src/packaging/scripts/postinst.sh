#!/bin/sh
set -e
chown -R pf4boot:pf4boot /opt/pf4boot/cross-plugin-jpa-sample
systemctl daemon-reload
systemctl enable pf4boot-cross-plugin-jpa-sample
systemctl start pf4boot-cross-plugin-jpa-sample || true
