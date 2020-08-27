#!/bin/bash
name=@app_name@
if [[ "$1" = "1" ]]; then
service $name start
fi
if [[ "$1" = "2" ]]; then
service $name restart
fi
