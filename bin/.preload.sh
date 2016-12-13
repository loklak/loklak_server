#!/usr/bin/env bash

INSTALLATIONCONFIG="data/settings/installation.txt"
PIDFILE="data/loklak.pid"
DFAULTCONFIG="conf/config.properties"
CUSTOMCONFIG="data/settings/customized_config.properties"
LOGCONFIG="conf/logs/log-to-file.properties"
STARTUPFILE="data/startup.tmp"
DFAULTXmx="-Xmx800m";
CUSTOMXmx=""

mkdir -p data/settings

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
