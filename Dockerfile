# NammaBasket: build the jar, then run it on a small Java 17 runtime image.
# docker build -t nammabasket . && docker run -p 8080:8080 --env-file .env nammabasket

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Dependencies first, so they are cached when only the code changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Run stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Run as a normal user, not root
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/grocery-web-app.jar app.jar
USER app
EXPOSE 8080
# -Xmx400m keeps the app inside a 512 MB free-tier container
ENTRYPOINT ["sh", "-c", "java -Xmx400m -Xss512k -jar app.jar"]
