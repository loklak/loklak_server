#!/usr/bin/env bash

# If you're looking for the variables, please go to bin/.preload.sh

# Make sure we're on project root
cd $(dirname $0)/..

# Execute preload script
source bin/.preload.sh

mkdir -p data

#to not allow process to overwrite the already running one.
if [ -f $PIDFILE ]; then
	PID=$(cat $PIDFILE 2>/dev/null)
	if [ $(ps -p $PID -o pid=) ]; then
		echo "Server is already running, please stop it and then start"
		exit 1
	else
		rm $PIDFILE
	fi
fi

if [ -f $DFAULTCONFIG ]; then
    j="$(grep Xmx $DFAULTCONFIG | sed 's/^[^=]*=//')";
    if [ -n $j ]; then DFAULTXmx="$j"; fi;
fi
if [ -f $CUSTOMCONFIG ]; then
    j="$(grep Xmx $CUSTOMCONFIG | sed 's/^[^=]*=//')";
    if [ -n $j ]; then CUSTOMXmx="$j"; fi;
fi

CLASSPATH=""
for N in lib/*.jar; do CLASSPATH="$CLASSPATH$N:"; done

if [ -d "./classes" ]; then
    CLASSPATH=".:./classes/:$CLASSPATH"
elif [ -d "./build/classes/main" ]; then
    CLASSPATH=".:./build/classes/main:$CLASSPATH"
else
    echo "It seems you haven't compile Loklak"
    echo "You can use either Gradle or Ant to build Loklak"
    echo "If you want to build with Ant,"
    echo "$ ant"
    echo "If you want to build with Gradle,"
    echo "$ gradle build"
    exit 1
fi

cmdline="java";

if [ -n "$ENVXmx" ] ; then cmdline="$cmdline -Xmx$ENVXmx";
elif [ -n "$CUSTOMXmx" ]; then cmdline="$cmdline -Xmx$CUSTOMXmx";
elif [ -n "$DFAULTXmx" ]; then cmdline="$cmdline -Xmx$DFAULTXmx";
fi

echo "starting loklak installation"
echo "startup" > $STARTUPFILE

cmdline="$cmdline -server -classpath $CLASSPATH -Dlog4j.configurationFile=$LOGCONFIG org.loklak.LoklakInstallation >> data/loklak.log 2>&1 &";

eval $cmdline
PID=$!
echo $PID > $PIDFILE

while [ -f $STARTUPFILE ] && [ $(ps -p $PID -o pid=) ]; do
	if [ $(cat $STARTUPFILE) = 'done' ]; then
		break
	else
		sleep 1
	fi
done

if [ -f $STARTUPFILE ] && [ $(ps -p $PID -o pid=) ]; then
	CUSTOMPORT=$(grep -iw 'port.http' conf/config.properties | sed 's/^[^=]*=//' );
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
