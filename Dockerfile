FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests && cp target/roomify-backend-*.jar app.jar

FROM eclipse-temurin:21.0.12_8-jre
WORKDIR /app
RUN useradd --create-home --uid 10001 roomify && mkdir -p /app/storage && chown roomify /app/storage
COPY --from=build /app/app.jar app.jar
USER roomify
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
