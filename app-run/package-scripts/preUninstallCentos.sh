#!/bin/bash
name=@app_name@
if [ "$1" == "0" ]; then
service $name stop
fi