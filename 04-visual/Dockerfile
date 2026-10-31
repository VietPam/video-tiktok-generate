FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache fontconfig ttf-dejavu
WORKDIR /app
COPY assets /app/assets
COPY build/libs/*-boot.jar app.jar
EXPOSE 38104
ENV SERVER_PORT=38104
HEALTHCHECK --interval=5s --timeout=3s --retries=3 CMD wget -q -O - http://localhost:38104/health || exit 1
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
