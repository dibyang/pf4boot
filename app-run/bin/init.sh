#!/bin/sh
# chkconfig: - 80 30
# description: graft-admin startup script
#
export LANG="en_US.utf8"

name=@app_name@
app_root=/@app_name@
start=${app_root}/bin/startup.sh
stop=${app_root}/bin/shutdown.sh

if cat /etc/issue | grep "Red Hat" &> /dev/null; then
OS="RH"
elif cat /etc/issue | grep "CentOS" &> /dev/null; then
OS="RH"
elif cat /etc/issue | grep "SUSE" &> /dev/null; then
OS="SUSE"
else
OS="RH"
#echo -n "This OS is not supported."
#exit 1
fi

if [ "$OS" = "SUSE" ]; then
. /etc/rc.status
rc_reset
fi

rh_start() {
    echo ""
    echo -n "Starting ${name}: "
    sh ${start}
    RETVAL=$?
    echo
    [ $RETVAL -eq 0 ] 
}

rh_stop() {
    echo ""
    echo -n "Shutting down ${name}: "
    sh ${stop}
    RETVAL=$?
    echo
    [ $RETVAL -eq 0 ]
}

rh_restart() {
    instance=`ps -ef | grep app.home=$app_root | sed '/grep/d'`
    if [ -n "$instance" ]; then
        rh_stop
	while true
        do
            temp=`ps -ef | grep app.home=$app_root | sed '/grep/d'`
            if [ -z "$temp" ];then
                break
            fi  
            sleep 1
        done
	rh_start
    else
        rh_start
    fi
}

rh_status() {
    instance=`ps -ef | grep app.home=$app_root | sed '/grep/d'`
    if [ -n "$instance" ]; then
        echo ""
        echo "${name} is running."
        echo ""
    else
        echo ""
        echo "${name} is stopped."
        echo ""
    fi
}


suse_start() {
    echo ""
    echo -n "Starting ${name}: "
    sh ${start}
}

suse_stop() {
    echo ""
    echo -n "Shutting down ${name}: "
    sh ${stop}
}

suse_restart() {
    instance=`ps -ef | grep app.home=$app_root | sed '/grep/d'`
    if [ -n "$instance" ]; then
        suse_stop
        while true
	do
	    temp=`ps -ef | grep felix | sed '/grep/d'`
	    if [ -z "$temp" ];then
		break
	    fi
	    sleep 1
	done
        suse_start
    else
        suse_start
    fi
}

suse_status() {
    instance=`ps -ef | grep app.home=$app_root | sed '/grep/d'`
    if [ -n "$instance" ]; then
        echo ""
        echo "${name} is running."
        echo ""
    else
        echo ""
        echo "${name} is stopped."
        echo ""
    fi

}

if [ "$OS" = "RH" ]; then
    case "$1" in
        start)
            rh_start
            ;;
        stop)
            rh_stop
            ;;
        restart)
            rh_restart
            ;;
        status)
            rh_status
            ;;
        *)
            echo "Usage: $0 {start|stop|restart|status}"
            exit 1
    esac
elif  [ "$OS" = "SUSE" ]; then
    case "$1" in
        start)
            suse_start
            ;;
        stop)
            suse_stop
            ;;
        restart)
            suse_restart
            ;;
        status)
            suse_status
            ;;
        *)
            echo "Usage: $0 {start|stop|restart|status}"
            exit 1
    esac
fi

if [ "$OS" = "RH" ]; then
exit $RETVAL
elif [ "$OS" = "SUSE" ]; then
rc_exit
fi
