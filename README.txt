loklak
======

loklak is a server application which is able to collect messages from
various sources, including twitter. The server contains a search index
and a peer-to-peer index sharing interface. All messages are stored in
a elasticsearch index.

'Lok Lak' is also a very tasty Cambodian stir-fry meat dish (usually
beef) with a LOT of fresh black pepper. If you ever have the chance to
eat Beef Lok Lak, please try it. I hope not to scare vegetarians with
this name, currently I am one as well.


*** Why should I use loklak?

If you like to be anonymous when searching things, want to archive
tweets or messages about specific topics and if you are looking for a
tool to create statistics about tweet topics, then you may consider
loklak. With loklak you can do:

- collect and store a very, very large amount of tweets
- create your own search engine for tweets
- omit authentication enforcement for API requests on twitter
- share tweets and tweet archives with other loklak users
- search anonymously on your own search portal
- create your own tweet search portal or statistical evaluations
- use Kibana to analyze large amounts of tweets for statistical data.


*** How can I build loklak myself?

- ant (just this, type "ant" - without quotes - and hit enter)

*** I do not have ant installed, How can I install it ?

- Mac OS X Systems
	- brew update
	- brew install ant

- Ubuntu
	- sudo apt-get install ant

*** How do I run loklak?

- build loklak (you need to do this only once, see above)
- run bin/start.sh
- open http://localhost:9100 in your browser
- to shut down loklak, run bin/stop.sh


*** Where can I find more information and documentation

The application has built-in documentation web pages, you will see
them when you opened the application web pages or you can simply open
html/index.html or just use http://loklak.org as reference.


*** What is the software license?

LGPL 2.1


*** There should be XXX and YYY can be enhanced!!

This project is considered a community work. There is no company behind
loklak. The development crew consist of YOU also. I am very thankful
for pull request. So if you discovered that something can be enhanced,
please do it yourself and send me a pull request. If you find a bug,
please try to fix it. If you report a bug to me I will possibly
consider it but at the very end of a giant, always growing heap of
work. The best chance for you to get things done is to try it yourself.


*** Where can I report bugs?

Please see above.


*** Where can I download ready-built releases of loklak?

Nowhere, you must clone the git repository of loklak and built it
yourself. Thats easy, just do
- git clone https://github.com/Orbiter/loklak.git
- cd loklak
- then see above ("How do I run loklak")


*** Where can I get the latest news about loklak?

Hey, this is the tool for that! Just put
http://loklak.org/api/search.rss?q=%23loklak into your rss reader.
Oh wait.. you will get a lot of information about tasty Cambodian food
with that as well. Alternatively you may also read the authors timeline
using http://loklak.org/api/search.rss?q=0rb1t3r
or just follow @0rb1t3r (thats a zero after the at sign)

Have fun!
@0rb1t3r
2015-03-07 Frankfurt am Main
