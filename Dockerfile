FROM gradle:9.7.1-jdk25 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser && mkdir -p /app/exercise-images && chown appuser:appuser /app/exercise-images
COPY --from=build /workspace/build/libs/pjarek-gym-0.1.0.jar /app/app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
