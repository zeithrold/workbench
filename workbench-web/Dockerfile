FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN groupadd --system workbench && useradd --system --gid workbench workbench

COPY build/libs/workbench-web.jar app.jar

USER workbench

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
