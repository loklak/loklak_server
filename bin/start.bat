cd ..
START java -Xmx4G -Xms1G -server -XX:+AggressiveOpts -XX:NewSize=512M -cp "build/libs/loklak_server-all.jar" org.loklak.LoklakServer >> data/loklak.log 2>&1 & echo $! > data/loklak.pid
echo "loklak server started at port 9000, open your browser at http://localhost:9000"
