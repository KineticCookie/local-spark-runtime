FROM alanszp/alpine-scala-sbt

ENV SERVER_HOME=/usr/share/swirl
ENV SERVER_JAR=target/scala-2.11/swirlish-assembly-1.0.jar

COPY . ${SERVER_HOME}

WORKDIR ${SERVER_HOME}

RUN sbt assembly
RUN cp ${SERVER_JAR} server.jar

RUN chmod +x docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["./docker-entrypoint.sh"]