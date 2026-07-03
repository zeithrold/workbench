FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN groupadd --system workbench && useradd --system --gid workbench workbench

COPY docker/jvm-entrypoint.sh /usr/local/bin/jvm-entrypoint.sh
RUN chmod 755 /usr/local/bin/jvm-entrypoint.sh

COPY workbench-worker/build/libs/workbench-worker.jar app.jar

# Target deployment: 512Mi memory limit (heap ~65% via MaxRAMPercentage; G1 default).
ENV WORKBENCH_GC_LOG_DIR=/tmp/gc \
    WORKBENCH_HEAP_DUMP_DIR=/tmp \
    WORKBENCH_JVM_OPTS="-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError"

USER workbench

ENTRYPOINT ["/usr/local/bin/jvm-entrypoint.sh"]
