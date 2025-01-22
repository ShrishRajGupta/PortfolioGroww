
FROM openjdk:17-jdk-slim

ADD target/demo-application.jar demo-application.jar

EXPOSE 8080

# Using MySQL 8 as per docker-compose
ENV MYSQL_ROOT_PASSWORD=rootpassword \
    MYSQL_DATABASE=mydb \
    MYSQL_USER=user\
    MYSQL_PASSWORD=userpassword\
    MYSQL_HOST=host.docker.internal

# Kafka Service Configuration
# Ensures Kafka is reachable by the application
ENV KAFKA_BROKER_ID=1 \
    KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT \
    KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1

ENTRYPOINT ["java", "-jar", "demo-application.jar"]
