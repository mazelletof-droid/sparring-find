# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
# Use a local maven repo cache to speed up builds in CI if available
RUN mvn -B -U -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy jar from build stage - adjust artifact name if necessary
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
