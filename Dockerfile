FROM openjdk:8-alpine AS builder
LABEL maintainer="Michael Peter Christen <mc@yacy.net>"

# build the image with (i.e.)
# docker build -t loklak/loklak_server:latest .

# start the image with (i.e.)
# docker run -d -p 9000:9000 <image>

# copy the required parts of the source code
ADD src /loklak_server/src/
ADD gradle /loklak_server/gradle/
ADD gradlew /loklak_server/
ADD build.gradle /loklak_server/
ADD settings.gradle /loklak_server/

# compile loklak
RUN cd /loklak_server && ./gradlew build --no-daemon -x checkstyleMain -x checkstyleTest -x jacocoTestReport

# Second Stage
FROM openjdk:8-jre-alpine

# install required software
RUN apk update && apk add --no-cache bash

# Create Volume for persistence
VOLUME ["/loklak_server/data"]

# loklak start
CMD ["/loklak_server/bin/start.sh", "-Idn"]

# setup locales
ENV LANG=en_US.UTF-8

# Expose the web interface ports
EXPOSE 9000 9443

# copy the required parts of the source code
WORKDIR /loklak_server/
COPY --from=builder /loklak_server/build/libs/loklak_server-all.jar build/libs/
ADD bin /loklak_server/bin/
ADD conf /loklak_server/conf/
ADD html /loklak_server/html/
ADD installation /loklak_server/installation/
ADD ssi /loklak_server/ssi/
ADD test/queries /loklak_server/test/queries/

# change config file
RUN sed -i 's/^\(upgradeInterval=\).*/\186400000000/' /loklak_server/conf/config.properties

# set current working directory to loklak_server
WORKDIR /loklak_server
