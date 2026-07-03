FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN groupadd --system workbench && useradd --system --gid workbench workbench

COPY docker/jvm-entrypoint.sh /usr/local/bin/jvm-entrypoint.sh
RUN chmod 755 /usr/local/bin/jvm-entrypoint.sh

COPY workbench-web/build/libs/workbench-web.jar app.jar

# Target deployment: 1Gi memory limit (heap ~70% via MaxRAMPercentage).
ENV WORKBENCH_GC_LOG_DIR=/tmp/gc \
    WORKBENCH_HEAP_DUMP_DIR=/tmp \
    WORKBENCH_JVM_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+UseZGC -XX:+ExitOnOutOfMemoryError"

USER workbench

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/jvm-entrypoint.sh"]
