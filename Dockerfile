# 1. Base 이미지로 OpenJDK 사용
FROM openjdk:17-jdk-slim

# 2. JAR 파일을 /app 디렉토리로 복사
COPY target/demo-0.0.1-SNAPSHOT.jar /app/demo.jar

# 3. 애플리케이션을 실행하는 명령어
ENTRYPOINT ["java", "-jar", "/app/demo.jar"]

