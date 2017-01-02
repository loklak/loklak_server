# Installing loklak server on Windows

Copy a fresh run install of a Loklak build from Linux box and copy some Java files.
You need to edit c:\loklak_server\bin\start.bat as well until the source is updated.

**Full install** skip Temporary source only download section and start at **Remove all Java.**

Temporary source only. (183-193 Mb download)
7z [Here](http://loklak.mnsnet.ca/loklak_server.7z)
Rar [Here](http://loklak.mnsnet.ca/loklak_server.rar)
Self extracting [Here](http://loklak.mnsnet.ca/loklak_server.exe)

**Remove all Java** from PC using Add Remove Programs. 
Download 32 Bit java from [here](http://download.cnet.com/Java-Development-Kit-32-bit/3000-2218_4-12091.html)

Install Cnet.com download of java. I had to Ignore the OS out of date messages. 
I Modified, As per solution for the java error. [here](http://stackoverflow.com/questions/18123144/missing-server-jvm-java-jre7-bin-server-jvm-dll)

1. After that go to `C:\Program Files\Java\jre1.8.0_66\bin`
2. Here create an folder called "Server" (without " ")
3. Now go into the `C:\Program Files\Java\jre1.8.0_66\bin\client` folder
4. COPY all data of this folder into the new `C:\Program Files\Java\jre1.8.0_66\bin\Server`

Just to be sure I repeated steps 1-4 with.

5. After that go to `C:\Program Files\Java\jdk1.8.0_66\bin`
6. Here create an folder called "Server" (without " ")
7. Now go into the `C:\Program Files\Java\jre1.8.0_66\bin\client` folder which is in the buffer anyway.
8. COPY all data of this folder into the new `C:\Program Files\Java\jdk1.8.0_66\bin\Server`

Cool it works but there is a java.exe window with the logfile in it, the START /b takes care of that.

Also I reduced the memory settings in the loklak_server\bin\start.bat file as well.

```
START /b java -Xmx1G -Xms1G -server -XX:+AggressiveOpts -XX:NewSize=512M -cp "classes;lib/*" org.loklak.LoklakServer >> data/loklak.log 2>&1 & echo $! > data/loklak.pid
echo "loklak server started at port 9000, open your browser at http://localhost:9000"
```
To bring Loklak to a graceful stop click the command prompt window once a wait, it will disappear.




#### **Installing a Loklak Server on Windows 32 bit.**

### Setting up git on windows
To use the git similar to how we use it on a linux terminal, Go to this [website](https://git-scm.com/downloads) and download the `git` for windows.
Install it step by step as follows:

1. Run the GIT installation executable.
2. After clicking Next on the welcome screen you will be asked to agree to the GNU General Public License.
3. Choose desired path `C:\Program Files\Git`
3.A Windows 7 install use recommended path. (Not sure yet but it works.)
4. On the Select Components page choose `Context Menu Entries` and leave the defaults for the others. Make sure that the `Git gui here` and `Git bash here` are selected under `Context Menu Entries`
5. For PATH environment you select `Use Git from the windows command prompt` option, this is needed for the automatic update on your loklak server to run later on if you decide to keep it.
6. For line ending conversion Select `Checkout As-Is commit Unix-style Line endings` option 
7. Click next and the automatic setup will complete the GIT installation with the selected options.

You can test out Git out in a command window if you wish and you did not download one and expand 1 of the zip files. 
You need a command window `Start Run` then type `cmd` and ok a black window will open in that type `cd\` then enter this should take you to `c:\` then type `git clone https://github.com/loklak/loklak_server.git` wait for it to finsh. You `can not` build/compile the Loklak server yet without `Apache Ant` see below.


### Installing Apache Ant for Windows.

I modified this blog. [blog](https://www.nczonline.net/blog/2012/04/12/how-to-install-apache-ant-on-windows/)

Download Apache Ant from 
[here](http://apache.uberglobalmirror.com//ant/binaries/apache-ant-1.9.6-bin.zip)
If mirror is down see 
[Mirror Down](https://ant.apache.org/bindownload.cgi) for the `zip` file.

1. Extract `apache-ant-1.9.6-bin.zip` to its default folder depending on your download location then **copy** the contents to a new folder created from Local Disk C: named ant eg `c:\ant`.

Windows XP:
To set environment variables on Windows XP.
Right click on My Computer and select Properties.
Then go to the Advanced tab and click the Environment Variables button at the bottom.

Click System Variables.
Click New then enter values as per below.
```
Variable Name `JAVA_HOME`
Variable Value `C:\Progra~1\Java\jdk18~1.0_6\jre`
```

To fix Ant compile also set this.
Click New.
```
Variable Name JAVA_TOOL_OPTIONS
Variable Value '-Dfile.encoding=UTF-8'
```

Windows 7: Repeat Windows XP To set environment variables and copy tools.jar.
 
Copy the file `tools.jar` in `C:\Program Files\Java\jdk1.8.0_66\lib\`
to `C:\Program Files\Java\jre1.8.0_66\lib`

(I will look into that later and see why the path problem is.)

Edit Variable `Path` and add `;c:\ant\bin;C:\Progra~1\Java\jdk18~1.0_6\jre\bin` to the end of the line.
Click OK 3 times and Ant is now installed you are ready to **build a loklak server.**

### Final steps.
You need open a command window `Start Run` then type `cmd` and ok a black window will open in that type `cd\` then enter this should take you to `c:\` then type `git clone https://github.com/loklak/loklak_server.git` wait for it to finsh then type `cd loklak_server` then type `ant` when ant has finished type `cd bin`.
You must modify `start.bat` in the loklak_server/bin folder by typing `edit start.bat`
```
cd ..
md data
START /b javaw -Xmx1G -Xms1G -server -XX:+AggressiveOpts -XX:NewSize=512M -cp "classes;lib/*" org.loklak.LoklakServer >> data/loklak.log 2>&1 & echo $! > data/loklak.pid
echo "loklak server started at port 9000, open your browser at http://localhost:9000"
```
Save changes and exit.
 
Type `start.bat` enter then wait for your browser to open you own Loklak Server with the address of `http://localhost:9000` also bookmark this for when you close your internet browser and reopen it again.

### Installed Finshed and running.


### Workarounds
If you have you loklak server from 1 of the zip files and want to contiune using it after completing the `Git` and `Ant` install simply run upgrade.sh from a command window to update to the latest loklak server version. You must then run start.bat to restart your loklak server. 

In the folder `c:\loklak_server\bin` there is a `start a BAT file` create a shortcut to desktop and double click `once` after PC starts. 

At the moment there are no shutdown controls for a loklak server.
By using `javaw` in `start.bat` in place of `java` there is no command window open anymore.
You must use task manager end task on javaw.exe to stop your `loklak server`, reboot or shutdown. 
  
When using Loklak if you hit enter on a search a `JSON` page will open to get the URL.
If you want a RSS Feed you must click the `Green Button` and you will be able to copy the URL into your RSS Reader.

### End of Workarounds

Must be Moved later on after testing.
For Windows 7: (Think this is for 64 bit will try later on.)
To set environment variables on Windows 7.
Right click on My Computer and select Properties.
Then go to the Advanced tab and click the Environment Variables button at the bottom.

Click System Variables.
Click New.
```
Variable Name JAVA_HOME
Variable Value C:\Progra~2\Java\jdk18~1.0_6\jre
```

