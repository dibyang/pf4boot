#!/bin/bash
name=@app_name@
systemctl stop $name
systemctl disable $name