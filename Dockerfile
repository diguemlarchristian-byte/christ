# Etape 1 : compilation avec Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Etape 2 : image d'execution allegee (JRE seul, pas le JDK complet)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/administration.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
