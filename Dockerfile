FROM apache/couchdb:2.3.1
MAINTAINER Samuel Tardieu, sam@rfc1149.net
RUN mkdir -p /usr/share/man/man1
RUN apt-get update && \
    apt-get install -y --no-install-recommends openjdk-11-jdk-headless git make
RUN useradd -m -c "Steenwerck" -s /bin/bash steenwerck
COPY . /tmp/stk
WORKDIR /tmp/stk
RUN make bin/replicate

FROM apache/couchdb:2.3.1
MAINTAINER Samuel Tardieu, sam@rfc1149.net
RUN mkdir -p /usr/share/man/man1
RUN apt-get update && \
    apt-get install -y --no-install-recommends openjdk-11-jre-headless && \
    rm -rf /var/lib/{apt,dpkg}
ADD docker/cors.ini /opt/couchdb/etc/local.d/
ADD docker/start.sh /
RUN chmod 755 /start.sh
ENTRYPOINT [ "/start.sh" ]
RUN useradd -m -c "Steenwerck" -s /bin/bash steenwerck
COPY --from=0 /tmp/stk/bin/replicate /usr/local/bin/
