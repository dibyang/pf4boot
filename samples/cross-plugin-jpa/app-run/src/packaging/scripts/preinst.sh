#!/bin/sh
set -e
if ! getent group pf4boot >/dev/null; then
    groupadd --system pf4boot
fi
if ! getent passwd pf4boot >/dev/null; then
    useradd --system --gid pf4boot --no-create-home --shell /usr/sbin/nologin pf4boot
fi
