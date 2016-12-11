#!/usr/bin/env sh
cd `dirname $0`/..
mkdir -p data

DFAULTCONFIG="conf/config.properties"
CUSTOMCONFIG="data/settings/customized_config.properties"
LOGCONFIG="conf/logs/log4j2.properties"
DFAULTXmx="-Xmx800m";
CUSTOMXmx=""
if [ -f $DFAULTCONFIG ]; then
 j="`grep Xmx $DFAULTCONFIG | sed 's/^[^=]*=//'`";
 if [ -n $j ]; then DFAULTXmx="$j"; fi;
fi
if [ -f $CUSTOMCONFIG ]; then
 j="`grep Xmx $CUSTOMCONFIG | sed 's/^[^=]*=//'`";
 if [ -n $j ]; then CUSTOMXmx="$j"; fi;
fi

#echo "DFAULTXmx: !$DFAULTXmx!"
#echo "CUSTOMXmx: !$CUSTOMXmx!"

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

echo "starting loklak"

cmdline="$cmdline -server -classpath $CLASSPATH -Dlog4j.configurationFile=$LOGCONFIG org.loklak.LoklakServer";

eval $cmdline
#echo $cmdline;

echo "loklak server started at port 9000, open your browser at http://localhost:9000"
