FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN ./gradlew :migrator:build -x test

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN mkdir -p /var/log && mkdir -p /var/run

COPY --from=build /app/migrator/build/libs/migrator-*.jar /app/migrator.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
COPY docker/config.yml /app/config.yml
COPY migrator/migrator-daemon.sh /app/migrator-daemon.sh

RUN chmod +x /app/entrypoint.sh /app/migrator-daemon.sh

ENV CONFIG_PATH=/app/config.yml
ENV DAEMON_INTERVAL=3600

ENTRYPOINT ["/app/entrypoint.sh"]
