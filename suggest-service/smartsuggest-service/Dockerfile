FROM alpine:latest

RUN apk update && apk add openjdk8-jre-base

EXPOSE 8081
WORKDIR /data

ENV JAVA_OPTS=""
ENTRYPOINT exec java -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=docker $JAVA_OPTS -jar /app/main.jar

ADD target/lib /app/lib
ARG JAR_FILE
ADD target/${JAR_FILE} /app/main.jar

RUN adduser -h /app -D app && \
    chown -R app:app /data && \
    chown -R app:app /app
USER app
