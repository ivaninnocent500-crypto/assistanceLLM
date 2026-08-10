# syntax=docker/dockerfile:1.6
# -------- Build stage --------
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace

# Resolve dependencies first for better layer caching
COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts gradle.properties ./
RUN gradle --no-daemon dependencies --quiet || true

# Now build the fat jar
COPY --chown=gradle:gradle src ./src
RUN gradle --no-daemon clean shadowJar

# -------- Runtime stage --------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/build/libs/decision-assistant-all.jar /app/app.jar

ENV PORT=8080 \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8" \
    RATE_LIMIT_PER_MINUTE=30 \
    RATE_LIMIT_PER_HOUR=200

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
