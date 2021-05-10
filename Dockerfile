FROM adoptopenjdk:15-jdk AS build

RUN apt-get update && apt-get upgrade -y && apt-get install -y git tree

WORKDIR build
# Copy gradle resources
COPY build.gradle.kts gradle.properties settings.gradle.kts gradlew ./
COPY gradle ./gradle
COPY buildSrc ./buildSrc
# Build deps
RUN ./gradlew build -x test
# Copy everything else
COPY . .
# Build artifact
RUN ./gradlew distTar
RUN ./gradlew :showVersion
RUN mv build/distributions/sightingdb-$(./gradlew :showVersion -q -Prelease.quiet | cut -d' ' -f2).tar dist.tar

FROM adoptopenjdk:16-jre
ENV CLASSPATH=""
ENV JAVA_OPTS="-Dconfig.file=/opt/sightingdb/conf/application.conf"
ENV SIGHTINGDB_OPTS=""

WORKDIR /opt/sightingdb
COPY --from=build build/dist.tar .
RUN tar xvf dist.tar --strip-components=1
RUN rm dist.tar

ENTRYPOINT /opt/sightingdb/bin/sightingdb
