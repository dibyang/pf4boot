#!/bin/bash
name=@app_name@

oldpath=$(pwd)
basepath=$(cd `dirname $0`; pwd)

approot=`dirname $basepath`
echo "app home=$approot"

instance=`ps -ef | grep app.home=$approot | sed '/grep/d'| awk '{print $2}'`
if [ -z "$instance" ]; then
      echo "$name is not running."
	exit 0
fi

kill $instance
echo "$name stopped"

