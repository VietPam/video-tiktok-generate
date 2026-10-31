FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY assets/images /app/assets/images
COPY build/libs/*-boot.jar app.jar
EXPOSE 38106
ENV SERVER_PORT=38106
HEALTHCHECK --interval=5s --timeout=3s --retries=3 CMD wget -q -O - http://localhost:38106/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
