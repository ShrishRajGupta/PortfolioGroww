
FROM openjdk:17-jdk-slim

ADD target/demo-application.jar demo-application.jar

EXPOSE 8080

# Defaults match docker-compose.yaml; override per environment.
# (Only the application's own settings belong here - broker/server env vars do not.)
ENV MYSQL_HOST=host.docker.internal \
    MYSQL_DATABASE=mydb \
    MYSQL_USER=user \
    MYSQL_PASSWORD=userpassword \
    KAFKA_BOOTSTRAP_SERVERS=kafka:9092

ENTRYPOINT ["java", "-jar", "demo-application.jar"]
