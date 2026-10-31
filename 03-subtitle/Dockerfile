FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*-boot.jar app.jar
EXPOSE 38103
ENV SERVER_PORT=38103
HEALTHCHECK --interval=5s --timeout=3s --retries=3 CMD wget -q -O - http://localhost:38103/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
