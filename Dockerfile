# ai-hub 数据面/控制面镜像（REQ-5.1）
# 用法：docker build --build-arg JAR_FILE=ai-hub-gateway/target/ai-hub-gateway-0.0.1-SNAPSHOT-exec.jar -t ai-hub-gateway .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
ARG JAR_FILE
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/${JAR_FILE} /app/app.jar
EXPOSE 18080 18081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
