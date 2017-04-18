# loklak
[![Join the chat at https://gitter.im/loklak/loklak](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/loklak/loklak)
[![Docker Pulls](https://img.shields.io/docker/pulls/mariobehling/loklak.svg?maxAge=2592000?style=flat-square)](https://hub.docker.com/r/mariobehling/loklak/)
[![Percentage of issues still open](http://isitmaintained.com/badge/open/loklak/loklak_server.svg)](http://isitmaintained.com/project/loklak/loklak_server "Percentage of issues still open")
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/loklak/loklak_server.svg)](http://isitmaintained.com/project/loklak/loklak_server "Average time to resolve an issue")
[![Twitter](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Wow%20Check%20Loklak%20on%20@gitHub%20@loklak_app%20@lklknt:%20https://github.com/loklak/loklak_server%20&url=%5Bobject%20Object%5D)
[![Twitter Follow](https://img.shields.io/twitter/follow/lklknt.svg?style=social&label=Follow&maxAge=2592000?style=flat-square)](https://twitter.com/lklknt)

Development: [![Build Status](https://travis-ci.org/loklak/loklak_server.svg?branch=development)](https://travis-ci.org/loklak/loklak_server) [![](https://dockerbuildbadges.quelltext.eu/status.svg?repository=loklak&organization=mariobehling)](https://hub.docker.com/r/mariobehling/loklak/builds/)
Master: [![Build Status](https://travis-ci.org/loklak/loklak_server.svg?branch=master)](https://travis-ci.org/loklak/loklak_server)

loklak is a server application which is able to collect messages from various sources, including twitter. The server contains a search index and a peer-to-peer index sharing interface. All messages are stored in an elasticsearch index. An automatic deployment from the development branch at GitHub is available for tests here https://loklak-server-dev.herokuapp.com

'Lok Lak' is also a very tasty Cambodian stir-fry meat dish (usually beef) with a LOT of fresh black pepper. If you ever have the chance to eat Beef Lok Lak, please try it. I hope not to scare vegetarians with this name, currently I am one as well.

## Communication

Please join our mailing list to discuss questions regarding the project: https://groups.google.com/forum/#!forum/loklak

Our chat channel is on gitter here: https://gitter.im/loklak/loklak

## Why should I use loklak?

If you like to be anonymous when searching things, want to archive tweets or messages about specific topics and if you are looking for a tool to create statistics about tweet topics, then you may consider loklak. With loklak you can:

- collect and store a very, very large amount of tweets
- create your own search engine for tweets
- omit authentication enforcement for API requests on twitter
- share tweets and tweet archives with other loklak users
- search anonymously on your own search portal
- create your own tweet search portal or statistical evaluations
- use [Kibana](https://github.com/elastic/kibana) to analyze large amounts of tweets for statistical data. [Tweets analytics with Kibana example.](http://blog.loklak.net/tweet-analytics-with-loklak-and-kibana-as-a-search-front-end/)

## How do I install loklak: Download, Build, Run

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)
[![Deploy on Scalingo](https://cdn.scalingo.com/deploy/button.svg)](https://my.scalingo.com/deploy?source=https://github.com/loklak/loklak_server)
[![Deploy to Bluemix](https://bluemix.net/deploy/button.png)](https://bluemix.net/deploy?repository=https://github.com/loklak/loklak_server)
[![Deploy to Docker Cloud](https://files.cloud.docker.com/images/deploy-to-dockercloud.svg)](https://cloud.docker.com/stack/deploy/)

At this time, loklak is not provided in compiled form, you easily build it yourself. It's not difficult and done in one minute! The source code is hosted at https://github.com/loklak/loklak_server, you can download it and run loklak with:

    > git clone https://github.com/loklak/loklak_server.git
    > cd loklak_server
    > ant

This command may give an error while building like `Unable to locate tools.jar`.
It means that there is no java in your system or JAVA_HOME Environment Variable is not set. [Refererence](https://www.digitalocean.com/community/tutorials/how-to-install-java-on-ubuntu-with-apt-get) to fix it and run `ant` again.

    > bin/start.sh

After all server processes are running, loklak tries to open a browser page itself. If that does not happen, just open http://localhost:9000; if you made the installation on a headless or remote server, then replace 'localhost' with your server name.

To stop loklak, run: (this will block until the server has actually terminated)

    > bin/stop.sh

A self-upgrading process is available which must be triggered by a shell command. Just run:

    > bin/upgrade.sh

### Where can I download ready-built releases of loklak?

Nowhere, you must clone the git repository of loklak and build it yourself. That's easy, just do
- `git clone https://github.com/loklak/loklak_server.git`
- `cd loklak`
- then see above ("How do I run loklak")

### How do I install loklak with Docker?
To install loklak with Docker please refer to the [loklak Docker installation readme](/docs/installation/installation_docker.md).

### How do I deploy loklak with Heroku?
You can easily deploy to Heroku by clicking the Deploy to Heroku button above. To install loklak using Heroku Toolbelt, please refer to the [loklak Heroku installation readme](/docs/installation/installation_heroku.md).

### How do I deploy loklak with cloud9?
To install loklak with cloud9 please refer to the [loklak cloud9 installation readme](/docs/installation/installation_cloud9.md).

### How do I setup loklak on Eclipse?

To install loklak on Eclipse, please refer to the [loklak Eclipse readme](/docs/development/eclipseSetup.md).

### How do I run loklak?

- build loklak (you need to do this only once, see above)
- run `bin/start.sh`
- open `http://localhost:9000` in your browser
- to shut down loklak, run `bin/stop.sh`

## How do I analyze data acquired by loklak

loklak stores data into an elasticsearch index. There is a front-end
for the index available in elasticsearch-head. To install this, do:
- `sudo npm install -g grunt-cli`
- `cd` into the parent directly of loklak_server
- `git clone git://github.com/mobz/elasticsearch-head.git`
- `cd elasticsearch-head`
- `npm install`

Run elasticsearch-head with:
- `grunt server`
..which opens the administration page at `http://localhost:9100`

## How do I configure loklak?

The basis configuration file is in ```conf/config.properties```. To customize these settings place a file ```customized_config.properties``` to the path ```data/settings/```
[See documentation](http://dev.loklak.org/) for details.

## Where can I find documentation?

The application has built-in documentation web pages, you will see them when you opened the application web pages or you can simply open `html/index.html` or just use http://loklak.org as reference. 

### Where can I find showcases and tutorials?

Articles and tutorials are also on our blog at http://blog.loklak.net.

### Where do I find the Java documentation?

At http://loklak.github.io/loklak_server/ or by building them via 'ant javadoc'

### Where can I get the latest news about loklak?

Hey, this is the tool for that! Just put http://loklak.org/api/search.rss?q=%23loklak into your rss reader. Oh wait.. you will get a lot of information about tasty Cambodian food with that as well. Alternatively you may also read the authors timeline using http://loklak.org/api/search.rss?q=0rb1t3r or just follow @0rb1t3r (that's a zero after the at sign)

## How to compile using Gradle?
- To install Gradle on Ubuntu:

  ```
  $ sudo add-apt-repository ppa:cwchien/gradle

  $ sudo apt-get update

  $ sudo apt-get install gradle
  ```
- To install Gradle on Mac OS X with homebrew

  ```
  brew install gradle
  ```

  Compile the source to classes and a jar file

  ```
  gradle build
  ```

  Compiled file can be found in build dir
  
  To remove compiled classes and jar file

  ```
  gradle clean
  ```


## What is the software license?

[LGPL 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1)


## Where can I report bugs and make feature requests?

This project is considered a community work. The development crew consist of YOU too. I am very thankful for pull request. So if you discovered that something can be enhanced, please do it yourself and make a pull request. If you find a bug, please try to fix it. If you report a bug to me I will possibly consider it but at the very end of a giant, always growing heap of work. The best chance for you to get things done is to try it yourself. Our [issue tracker is here][issues].


## How can I contribute?

There are a lot of [issues][issues] you can solve.
If you are here for the first time, you can look at [`first-timers-only` issues][first-timers-issues].
They are either

- easy to do and introduce you to github and git
- or descripe clearly how to solve the problem, so you have smoother start.

If you want to solve an issue, please comment in it that you would like to solve it.
In any case, if you run into problems, please report them in the issue.


Have fun!
@0rb1t3r


[issues]: https://github.com/loklak/loklak_server/issues
[first-timers-issues]: https://github.com/loklak/loklak_server/issues?q=is%3Aissue+is%3Aopen+label%3Afirst-timers-only
