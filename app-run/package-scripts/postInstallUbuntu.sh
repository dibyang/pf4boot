#!/bin/bash
name=@app_name@
systemctl enable $name
systemctl start $name