# Build stage: compile and package the Spring Boot jar with the Maven Wrapper.
# Tests are skipped here because the integration tests need Testcontainers (Docker);
# run `./mvnw verify` outside the image build.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until the pom changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/support-inbox-*.jar app.jar

# Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

EXPOSE 8080

# Memory flags for a 512 MB container: cap the heap at 70% of the container limit,
# use the single-threaded GC and the fast C1 compiler to keep the footprint small.
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=70", \
  "-XX:+UseSerialGC", \
  "-XX:TieredStopAtLevel=1", \
  "-jar", "app.jar"]
