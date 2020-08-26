#!/bin/bash

if [[ "$1" = "1" ]]; then
service demo-app start
fi
if [[ "$1" = "2" ]]; then
service demo-app restart
fi
