FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN groupadd --system workbench && useradd --system --gid workbench workbench

COPY build/libs/workbench-worker.jar app.jar

USER workbench

ENTRYPOINT ["java", "-jar", "app.jar"]
