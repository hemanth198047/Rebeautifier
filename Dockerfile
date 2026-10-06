# syntax=docker/dockerfile:1

FROM node:22-bookworm AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS api-build
WORKDIR /app
COPY pom.xml .
COPY src src
COPY --from=frontend-build /frontend/dist/ src/main/resources/static/
# Use the image Maven directly (avoids Windows CRLF breaking ./mvnw in Docker).
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/data/covers
COPY --from=api-build /app/target/rebeautifier.jar /app/app.jar
COPY docker/api-entrypoint.sh /app/api-entrypoint.sh
# Normalize CRLF if the script was checked out on Windows.
RUN sed -i 's/\r$//' /app/api-entrypoint.sh && chmod +x /app/api-entrypoint.sh
ENV SERVER_PORT=8080 \
    COVER_STORAGE_PATH=/app/data/covers \
    SEED_DEMO_DATA=true \
    APP_VERSION=0.6.0
EXPOSE 8080
VOLUME ["/app/data"]
HEALTHCHECK --interval=10s --timeout=5s --start-period=50s --retries=12 \
  CMD curl -fsS http://127.0.0.1:8080/api/v1/health/ready || exit 1
ENTRYPOINT ["/app/api-entrypoint.sh"]
