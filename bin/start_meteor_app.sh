if type meteor >/dev/null ; then
cd ../html/apps/meteor-twitterwall/;
meteor --settings settings.json;
else
    echo "MeteorJS not installed";
fi
