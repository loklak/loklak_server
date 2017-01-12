# Download and Installation

`loklak` is free software, licensed with LGPL. To install `loklak`, you need JDK 1.8, git and ant. If you don't know what this is, then `loklak` is currently not something for you.

At this time, `loklak` is not provided in compiled form, you must build it yourself. It's not difficult and done in one minute!

***

### Download, Build, Run

The source code is hosted at https://github.com/loklak/loklak_server, you can download it and run `loklak` with:
```
   > git clone https://github.com/loklak/loklak_server.git
   > cd loklak_server
   > ant
   > bin/start.sh
```
After all server processes are running, loklak tries to open a browser page itself. If that does not happen, just open http://localhost:9000; if you made the installation on a headless or remote server, then replace 'localhost' with your server name.

To stop loklak, run: (this will block until the server has actually terminated)

   `> bin/stop.sh`

A self-upgrading process is available which must be triggered by a shell command. Just run:

   `> bin/upgrade.sh`
    
***

### Import A Message Dump

To import a message dump (which you get from the [dump directory](http://loklak.org/dump/) of every loklak peer), just move it to the `data/dump/import/` directory:
```
   loklak
      ⌊data
         ⌊dump
             ⌊import                                 // to import a dump, throw the dump in here, it will got to...
             ⌊imported                               // processed dump files from the import folder are moved here
             ⌊own                                    // dump files which this application creates, accessible at /dump/
```
Imported dumps are not deleted, but moved to the `imported` directory. Because extracted hashtags, links and user names
are not part of the dump, this is done during the import process and written to the elasticsearch index. While imports
are running, you can use the
[/api/status.json](https://github.com/loklak/loklak_server/blob/development/docs/api.md) servlet to monitor the import progress.

***

### Re-Build The Search Index

In case of application bugs, data structure changes or if you change your set-up for larger indexing shards, you can re-create the search index completely using the index dumps. To delete and re-create the index, do:
```
   stop loklak with bin/stop.sh
   delete the index folder at data/index
   move your dump files from data/dump/own/ to data/dump/import
   start loklak again - this will re-create the index folder
   the import starts automatically
```    
***

### Use Kibana As Search Front-End

Kibana is a tool to "explore and visualize your data". It is not actually a search front-end but you can use it as such. Because Kibana is made for elasticsearch, it will instantly fit on `loklak` without any modification or configuration. Here is what you need to do:

   * Download Kibana from http://www.elasticsearch.org/overview/kibana/installation/
   * Open a terminal, cd into the kibana directroy and run bin/kibana on linux or Mac, or bin/kibana.bat on Windows.
   * open http://localhost:5601

Kibana is pre-configured with default values to attach to an elasticsearch index containing logstash data. We will use a differnt index name than logstash: the `loklak` index names are 'messages' and 'users'. When the Kibana Settings page is visible in your browser, do:

   * On the 'Configure an index pattern' Settings-page of the kibana interface, enter "messages" (without the quotes) in the field "Index name or pattern".
   * As soon as you typed this in, another field "Time-field name" appears, with a red border and empty. Use the selectbox-arrows on the right side of the empty field to select one entry which is there: "created_at".
   * Push the 'Create' button.

A page with the name "messages" appears and shows all index fields of the `loklak` messages index. If you want to search the index from Kibana, do:

   * Click on "Discover" in the upper menu bar.
   * You may probably see a page with the headline "No results found". If your loklak index is not empty, this may be caused by a too tight time range; therefore the next step should solve that:
   * Click on the time picker in the top right corner of the window and select (i.e.) "This month".
   * A 'searching' Message appears, followed with a search result page and a histogram at the top.
   * replace the wild-card symbol '*' in the query input line with a word which you want to search, i.e. 'fossasia'
   * You can also select a time period using a click-drag over the histogram to narrow the search result.
   * You can click on the field names on the left border to show a field facet. Click on the '+'-sign at the facet item to activate the facet.

The remote search to twitter with the twitter scraper is not done using the elasticsearch 'river' method to prevent that
a user-frontend like Kibana constantly triggers a remote search. Therefore this search method with kibana will not help
to enrich your search index with remote search results. This also means that you won't see any results in Kibana until
you searched with the [/api/search.json](https://github.com/loklak/loklak_server/blob/development/docs/api.md) api.

***

### Use Nginx As Reverse Proxy

If you run `loklak` behind a nginx reverse proxy, it is important to forward the client IP address through the proxy. If you don't do that, `loklak` thinks that all requests come from localhost and are therefore all authorized to do anything with maximum access rights. To configure Nginx to forward the client IP address, add the following line to the server section of your config file:

   `proxy_set_header X-Real-IP $remote_addr;`

The full server section may then look similar to:
```
   server {
     listen 80;
     server_name myserver.mytld;
     location / {
       proxy_pass 127.0.0.1:9000;
       include /etc/nginx/proxy_params;
       proxy_set_header X-Real-IP $remote_addr;
       proxy_set_header Host $host;
     }
   }
```
***

### Change Config Parameters

The configuration initialization of `loklak` is in `conf/config.properties` but that file may be overwritten if you update the application. To make changes to the configuration persistent, there is another file located at `data/settings``/customized_config.properties` which overwrites the settings during startup time.

#### Change Elasticsearch Configuration Properties

These properties are included in the `loklak` properties file and prefixed with the string `elasticsearch`. You can add more elasticsearch properties here, all keys with the prefix "elasticsearch." are send to elasticsearch.

#### Change the Back-End Server

In the properties, there is a line `backend=http://loklak.org`. You can change this to your own server. This name is a prefix for the api path, so adding a port to the host name is possible.

***
