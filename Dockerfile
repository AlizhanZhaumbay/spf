# Используем OpenJDK 21
FROM openjdk:21-jdk-slim

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем JAR-файл
COPY target/spf-0.0.1-SNAPSHOT.jar /app/spf.jar

# Открываем порт
EXPOSE 8080

# Запускаем приложение
CMD ["java", "-jar", "/app/spf.jar"]
