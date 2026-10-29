#!/usr/bin/env sh
# Compiles and runs the Spring-free domain tests with plain javac (useful where Maven Central is not reachable).
# The same test files run under real JUnit 5 with `mvn test`.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d /tmp/tfjava.XXXXXX)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" $(find "$ROOT/scripts/junit-shim" -name '*.java') $(find "$ROOT/backend/src/main/java/in/trendsfit/domain" -name '*.java') $(find "$ROOT/backend/src/test/java/in/trendsfit/domain" -name '*.java') 2>&1 | grep -v JAVA_TOOL || true
CLASSES=$(cd "$ROOT/backend/src/test/java" && find in/trendsfit/domain -name '*Test.java' | sed 's/\.java$//; s#/#.#g' | tr '\n' ' ')
set +e
java -cp "$OUT" runner.MiniRunner $CLASSES > "$OUT/run.log" 2>&1
RC=$?
grep -v JAVA_TOOL "$OUT/run.log"
exit $RC
