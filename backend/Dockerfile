# Сборка
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Запуск
FROM eclipse-temurin:21.0.8_9-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /data/uploads && chown -R app:app /data
WORKDIR /app
COPY --from=build /src/target/credit-backend.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-Xms128m -Xmx512m"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
