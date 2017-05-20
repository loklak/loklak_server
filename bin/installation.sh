#!/usr/bin/env bash

# If you're looking for the variables, please go to bin/.preload.sh

# Make sure we're on project root
cd $(dirname $0)/..

# Execute preload script
source bin/.preload.sh
source bin/utility.sh

echo "starting loklak installation"
echo "startup" > $STARTUPFILE

while getopts ":p:" opt; do
    case $opt in
        p)
            PORT_NUMBER=$OPTARG
            ;;
        \?)
            echo -e " -p\tPort to start LoklakServer on"
            exit 1
            ;;
    esac
done

cmdline="$cmdline -server -classpath $CLASSPATH -Dlog4j.configurationFile=$LOGCONFIG org.loklak.LoklakInstallation $PORT_NUMBER >> data/loklak.log 2>&1 &";

eval $cmdline
PID=$!
echo $PID > $PIDFILE

while [ -f $STARTUPFILE ] && [ $(ps -p $PID -o pid=) ]; do
	if [ $(cat "$STARTUPFILE") = 'done' ]; then
		break
	else
		sleep 1
	fi
done

if [ -f $STARTUPFILE ] && [ $(ps -p $PID -o pid=) ]; then
    if [ "$PORT_NUMBER" ]; then
        CUSTOMPORT=$PORT_NUMBER
    else
	    CUSTOMPORT=$(grep -iw 'port.http' conf/config.properties | sed 's/^[^=]*=//' );
	fi
	change_shortlink_urlstub $CUSTOMPORT # function defined in utility.sh
	LOCALHOST=$(grep -iw 'shortlink.urlstub' conf/config.properties | sed 's/^[^=]*=//');
	echo "loklak installation started at port $CUSTOMPORT, open your browser at $LOCALHOST"
	rm -f $STARTUPFILE

    echo "waiting for installation to finish"
    wait "$PID"
    if [ $? -eq 0 ]; then
        echo "loklak installation finished"
        echo 'done' > $INSTALLATIONCONFIG
    else
        echo "loklak installation aborted"
    fi

	exit 0
else
	echo "loklak installation failed to start. See data/loklag.log for details. Here are the last logs:"
    tail data/loklak.log
	rm -f $STARTUPFILE
	exit 1
fi
