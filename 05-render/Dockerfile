FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache ffmpeg fontconfig ttf-dejavu
WORKDIR /app
COPY assets /app/assets
COPY build/libs/*-boot.jar app.jar
EXPOSE 38105
ENV SERVER_PORT=38105
HEALTHCHECK --interval=5s --timeout=3s --retries=3 CMD wget -q -O - http://localhost:38105/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
