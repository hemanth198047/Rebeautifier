# Rebeautifier

Application to read and beautify thoughts.

## Stack

- Java 17+
- Spring Boot 3.4
- Maven

## Project layout

```
src/main/java/com/rebeautifier/
  RebeautifierApplication.java   # entry point
  controller/                    # HTTP endpoints
  service/                       # business logic
  model/                         # domain models (placeholder)
src/main/resources/
  application.yml
```

## Run

Requirements: JDK 17+. Maven is optional if you use the included wrapper (`./mvnw`).

```bash
./mvnw spring-boot:run
```

Then open [http://localhost:8080](http://localhost:8080) for the welcome JSON response.

Build a runnable jar:

```bash
./mvnw -DskipTests package
java -jar target/rebeautifier-0.0.1-SNAPSHOT.jar
```

Run tests:

```bash
./mvnw test
```
