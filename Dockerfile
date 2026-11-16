# Frontend build stage
FROM node:26-alpine AS ui
WORKDIR /ui
COPY ui/package.json ui/package-lock.json ./
RUN npm ci
COPY ui/ ./
RUN npm run build

# Backend build stage
# Java 17 in both stages below: it is what pom.xml targets and what CI runs the test suite
# on, so the image ships on the runtime the tests actually exercised. Move all three together.
FROM maven:3.9.15-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
# The SPA is served from the same origin as the API; see the design spec for why.
COPY --from=ui /ui/dist ./src/main/resources/static
RUN mvn package -Dmaven.test.skip=true -q

# Run stage
FROM eclipse-temurin:17-jre-jammy
RUN groupadd -r app && useradd -r -g app -d /app -s /sbin/nologin app
WORKDIR /app
# Wildcard so a version bump in pom.xml does not also have to be made here. It matches
# exactly one file: the repackaged jar (the plain one is renamed to *.jar.original).
COPY --from=builder /app/target/activity-tracker-*.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
