# Stage 1: build the application
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Resolve dependencies first so that a source-only change does not re-download
# the whole Maven repository on every build.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B clean package -DskipTests

# Stage 2: run it
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl backs the health check below.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user rather than root.
RUN useradd --system --create-home --uid 10001 learnx

# Uploaded files live here; mount a volume over it so they outlive the container.
RUN mkdir -p /var/lib/learnx/files && chown -R learnx:learnx /var/lib/learnx

COPY --from=build --chown=learnx:learnx /app/target/*.jar app.jar

USER learnx
EXPOSE 8080

ENV LEARNX_STORAGE_ROOT=/var/lib/learnx/files \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://localhost:8080/ >/dev/null || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
