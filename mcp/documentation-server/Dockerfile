FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY backend/mvnw ./mvnw
COPY backend/.mvn/ ./.mvn/
COPY mcp/documentation-server/ ./
RUN chmod +x mvnw && ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --uid 10001 --create-home app
WORKDIR /app
COPY --from=build /build/target/documentation-mcp-server-0.0.1-SNAPSHOT.jar app.jar
USER 10001
ENV MCP_BIND_ADDRESS=0.0.0.0
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
