FROM alpine:latest
MAINTAINER Ansgar Schmidt <ansgar.schmidt@gmx.net>

# setup locales
ENV LANG=en_US.UTF-8

# Expose the web interface ports
EXPOSE 80 443

# copy the required parts of the source code
ADD bin /loklak_server/bin/
ADD conf /loklak_server/conf/
ADD src /loklak_server/src/
ADD lib /loklak_server/lib/
ADD html /loklak_server/html/
ADD installation /loklak_server/installation/
ADD ssi /loklak_server/ssi/
ADD build.xml /loklak_server/

# install OpenJDK 8 JDK, Ant, and Bash
RUN apk update && apk add openjdk8 apache-ant git bash && \
    # compile loklak
    cd /loklak_server && ant && \
    # change config file
    sed -i 's/^\(port.http=\).*/\180/;s/^\(port.https=\).*/\1443/;s/^\(upgradeInterval=\).*/\186400000000/' \
        conf/config.properties && \
    # remove OpenJDK 8 JDK and Ant
    apk del openjdk8 apache-ant git && \
    # install OpenJDK 8 JRE without GUI support
    apk add openjdk8-jre-base

# set current working directory to loklak_server
WORKDIR /loklak_server

# start loklak
CMD ["/loklak_server/bin/start.sh", "-Idn"]
