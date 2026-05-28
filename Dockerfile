# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy only the POM first to cache dependency downloads
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build the fat JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="TermDash Contributors"
LABEL description="Terminal-based system monitoring dashboard"

WORKDIR /app

# UTF-8 locale so Lanterna box-drawing characters render correctly
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV TERM=xterm-256color

# Default terminal size matching the dashboard layout (120x38)
ENV COLUMNS=120
ENV LINES=38

# Copy the shaded fat JAR from the build stage
COPY --from=build /app/target/termdash-1.0-SNAPSHOT.jar termdash.jar

# The TUI app needs an interactive terminal, so the container
# must be launched with:  docker run -it termdash
ENTRYPOINT ["java", "-jar", "termdash.jar"]
