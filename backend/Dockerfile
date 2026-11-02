FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package
RUN mkdir /leaky && javac -d /leaky src/test/java/com/heapvortex/mock/LeakyApp.java

FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /leaky /leaky
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh && mkdir -p /app/dumps
ENV HEAPVORTEX_DUMPS_DIR=/app/dumps
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]