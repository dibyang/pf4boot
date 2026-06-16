#!/bin/sh
set -e
systemctl stop pf4boot-cross-plugin-jpa-sample || true
systemctl disable pf4boot-cross-plugin-jpa-sample || true
