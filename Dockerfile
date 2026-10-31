# Baut die App im Container, damit auf dem Pi kein Java/Maven installiert sein muss.
# Beide Images gibt es auch für ARM (Raspberry Pi 4/5 mit 64-Bit-System).

# ---------- 1. Bauen ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Erst nur die pom.xml: die Abhängigkeiten werden so zwischengespeichert
# und nicht bei jeder Codeänderung neu heruntergeladen.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/*.jar app.jar

# ---------- 2. Laufen ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Europe/Vienna
# Nicht als root laufen. 1000 ist der Standard-Benutzer auf dem Pi, so bleibt ./data (Bilder) beschreibbar.
RUN mkdir -p /app/data && chown 1000:1000 /app/data
USER 1000:1000

COPY --from=build /build/app.jar /app/app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
