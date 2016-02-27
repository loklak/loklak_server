#!/usr/bin/env sh

# Check for an existing instance of server
# If it exists, shut it down
filepath="`dirname $0`/../data/loklak.pid"
if [ -f $filepath ]; then
 echo "shutting down existing loklak server"
 kill `cat $filepath` 2>/dev/null
 if [ $? -eq 0 ]; then while [ -f $filepath ]; do sleep 1; done fi;
 rm -f $filepath 2>/dev/null
fi

cd `dirname $0`/..
mkdir -p data

DFAULTCONFIG="conf/config.properties"
CUSTOMCONFIG="data/settings/customized_config.properties"
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
CLASSPATH=".:./classes/:$CLASSPATH"

cmdline="java";

if [ -n "$CUSTOMXmx" ]; then cmdline="$cmdline -Xmx$CUSTOMXmx";
elif [ -n "$DFAULTXmx" ]; then cmdline="$cmdline -Xmx$DFAULTXmx";
fi

echo "starting loklak"

cmdline="$cmdline -server -classpath $CLASSPATH org.loklak.LoklakServer >> data/loklak.log 2>&1 & echo \$! > data/loklak.pid &";

eval $cmdline
#echo $cmdline;

echo "loklak server started at port 9000, open your browser at http://localhost:9000"
