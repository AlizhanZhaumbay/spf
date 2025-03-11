FROM openjdk:21-jdk-slim

WORKDIR /app

COPY target/spf-0.0.1-SNAPSHOT.jar /app/spf-0.0.1-SNAPSHOT.jar

EXPOSE 8080

RUN apt-get update && apt-get install -y postgresql-client

CMD ["java", "-jar", "your-application.jar"]