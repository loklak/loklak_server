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
