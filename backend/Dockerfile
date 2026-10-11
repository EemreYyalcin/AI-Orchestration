# Java 25/JDK derleme aşaması; mevcut Maven Wrapper kullanılır. Image derlemesi testleri atlar,
# doğrulama ayrıca mvnw verify ile yapılmalıdır.
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /build/backend
COPY backend/ ./
RUN chmod +x mvnw && ./mvnw --batch-mode -DskipTests package

# Küçük JRE çalışma aşaması: paketlenmiş Spring uygulaması UID 10001 ile çalışır.
# curl yalnızca container sağlık kontrolünü destekler; orchestration kabuk aracı değildir.
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --uid 10001 --create-home app
WORKDIR /app
COPY --from=build /build/backend/target/ai-incident-orchestrator-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
