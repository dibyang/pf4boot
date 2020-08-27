#!/bin/bash

oldpath=$(pwd)
basepath=$(cd `dirname $0`; pwd)

name=@app_name@
approot=`dirname $basepath`
cd $approot

instance=`ps -ef | grep app.home=$approot | sed '/grep/d'`
if [ -z "$instance" ]; then
      echo "$name is not running."
	exit 0
fi

pkill -9  -f  app.home=$approot
echo "$name is stopped"

