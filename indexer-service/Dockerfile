#-----------------------------------------------------------------------------------------------------
# Elasticsearch Indexing-Service
#-----------------------------------------------------------------------------------------------------
FROM openjdk:8u151-jdk-alpine

WORKDIR /app

ENV JAVA_OPTS=""
ENTRYPOINT exec java -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=docker $JAVA_OPTS -jar /app/app.jar

ADD target/lib /app/lib
ARG JAR_FILE
ADD target/${JAR_FILE} /app/app.jar
