#!/bin/bash
name=@app_name@
oldpath=$(pwd)
basepath=$(cd `dirname $0`; pwd)

approot=`dirname $basepath`
cd $approot

instance=`ps -ef | grep app.home=$approot | sed '/grep/d'`
if [ -n "$instance" ]; then
    echo "$name is running."
	exit 0
fi

JAVA_VERSION=`java -version 2>&1 |awk 'NR==1{ gsub(/"/,""); print $3 }'`
if [ "$JAVA_VERSION" \< "1.8." ]; then
    echo "The JDK requires 1.8.x or above "
	exit 0
fi

JAVA_OPTS="-Dfile.encoding=utf-8 -Dsun.jnu.encoding=utf-8"
JAVA_OPTS="$JAVA_OPTS -cp :$approot/lib/* -Dapp.home=$approot"
#JAVA_OPTS="$JAVA_OPTS -Dlogback.statusListenerClass=ch.qos.logback.core.status.OnErrorConsoleStatusListener"
#JAVA_OPTS="$JAVA_OPTS -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5006"


eval java  $JAVA_OPTS  net.xdob.demo.Application \
 1> /dev/null 2> /dev/null "&"

instance=`ps -ef | grep app.home=$approot | sed '/grep/d'`
if [ -n "$instance" ]; then
  echo "$name started"
else
	echo "$name start failed"
fi



