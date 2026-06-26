# Bước 1: Build dự án bằng Maven
FROM maven:3.8.5-openjdk-17 AS build
COPY . .
RUN mvn clean package -DskipTests

# Bước 2: Chạy ứng dụng Java Spring Boot
FROM openjdk:17-jdk-slim
COPY --from=build /target/*.jar demo.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","demo.jar"]