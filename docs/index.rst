Welcome to the loklak Server documentation!
===========================================

.. toctree::
   :caption: Home
   :hidden:

   Welcome <self>

.. toctree::
   :caption: Installation
   :hidden:
   :maxdepth: 1

   Download and Installation <installation/download>
   Installation on Linux <installation/installation_linux>
   Installation on macOS <installation/installation_mac>
   Installation on Windows <installation/installation_windows>
   Installation with Docker <installation/installation_docker>
   Installation on Cloud9 <installation/installation_cloud9>
   Installation on Heroku <installation/installation_heroku>
   Video Tutorials <installation/tutorials>
   
.. toctree::
   :caption: Development
   :hidden:
   :maxdepth: 1

   API <development/api>
   Setup Eclipse <development/eclipseSetup>
   Javadoc <https://dev.loklak.org/javadoc>

.. toctree::
   :caption: Miscellaneous
   :hidden:
   :maxdepth: 1

   Architecture <misc/architecture>
   Parsers <misc/parsers>

loklak is a server application which is able to collect messages from various
sources, including twitter. The server contains a search index and a peer-to-peer
index sharing interface. All messages are stored in an elasticsearch index. An
automatic deployment from the development branch at GitHub is available for
tests here https://loklak-server-dev.herokuapp.com

'Lok Lak' is also a very tasty Cambodian stir-fry meat dish (usually beef) with
a LOT of fresh black pepper. If you ever have the chance to eat Beef Lok Lak,
please try it. I hope not to scare vegetarians with this name, currently I am
one as well.

Communication
-------------

Please join our mailing list to discuss questions regarding the project:
https://groups.google.com/forum/#!forum/loklak

Our chat channel is on gitter here: https://gitter.im/loklak/loklak

Why should I use loklak?
------------------------

If you like to be anonymous when searching things, want to archive tweets or
messages about specific topics and if you are looking for a tool to create
statistics about tweet topics, then you may consider loklak. With loklak you can:

 * collect and store a very, very large amount of tweets
 * create your own search engine for tweets
 * omit authentication enforcement for API requests on twitter
 * share tweets and tweet archives with other loklak users
 * search anonymously on your own search portal
 * create your own tweet search portal or statistical evaluations
 * use Kibana to analyze large amounts of tweets for statistical data.
