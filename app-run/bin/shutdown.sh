#!/bin/bash
name=@app_name@

oldpath=$(pwd)
basepath=$(cd `dirname $0`; pwd)

approot=`dirname $basepath`
cd $approot

instance=`ps -ef | grep app.home=$approot | sed '/grep/d'` | awk '{print $2}'
if [ -z "$instance" ]; then
      echo "$name is not running."
	exit 0
fi

pkill -9  -f  app.home=$approot
echo "$name stopped"

