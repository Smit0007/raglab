FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY rcache/target/raglab-rcache-0.1.0-SNAPSHOT.jar rcache.jar
EXPOSE 6380
ENTRYPOINT ["java", "-jar", "rcache.jar"]
