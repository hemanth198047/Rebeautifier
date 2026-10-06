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
COPY .mvn .mvn
COPY mvnw mvnw
COPY src src
COPY --from=frontend-build /frontend/dist/ src/main/resources/static/
RUN chmod +x mvnw && ./mvnw -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN mkdir -p /app/data/covers
COPY --from=api-build /app/target/rebeautifier-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker/api-entrypoint.sh /app/api-entrypoint.sh
RUN chmod +x /app/api-entrypoint.sh
ENV SERVER_PORT=8080 \
    COVER_STORAGE_PATH=/app/data/covers \
    SEED_DEMO_DATA=true
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["/app/api-entrypoint.sh"]
