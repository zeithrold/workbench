#!/bin/sh
set -eu

gc_log_dir="${WORKBENCH_GC_LOG_DIR:-/tmp/gc}"
heap_dump_dir="${WORKBENCH_HEAP_DUMP_DIR:-/tmp}"

mkdir -p "$gc_log_dir" "$heap_dump_dir"

# shellcheck disable=SC2086
exec java \
  ${WORKBENCH_JVM_OPTS:-} \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath="${heap_dump_dir}/heapdump.hprof" \
  -XX:ErrorFile="${heap_dump_dir}/hs_err_%p.log" \
  -Xlog:gc*,safepoint:file="${gc_log_dir}/gc-%p.log":time,level,tags:filecount=5,filesize=10M \
  -jar /app/app.jar
