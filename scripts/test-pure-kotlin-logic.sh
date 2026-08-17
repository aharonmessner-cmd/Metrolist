#!/usr/bin/env bash
# test-pure-kotlin-logic.sh — Standalone kotlinc+JUnit4 fallback for pure Kotlin logic.
#
# Some sandboxes/CI-less environments cannot reach Google's Maven repo (dl.google.com),
# so Gradle/AGP cannot resolve com.android.tools.build:gradle and NO Gradle task can run,
# not even a plain JVM unit test in an Android library module. This script is a fallback
# for that situation ONLY: it compiles and runs the project's dependency-free pure-Kotlin
# packages (no androidx/Media3/Room/Compose imports) directly with a standalone `kotlinc`
# + JUnit4 on the classpath, bypassing Gradle entirely.
#
# This does NOT replace real Gradle/AGP testing - it only proves the *logic* in these
# specific pure packages behaves as asserted. Whenever Gradle/AGP is available (e.g. in
# GitHub Actions), prefer the real commands documented in README/AGENTS.md instead
# (e.g. `./gradlew testGmsDebugUnitTest`), since only those exercise the actual production
# classpath, Android resources, and full dependency graph.
#
# Requirements (install once per sandbox if missing):
#   apt-get install -y kotlin junit4
#
# Usage:
#   scripts/test-pure-kotlin-logic.sh
#
# Exit codes:
#   0 - all discovered tests passed
#   1 - setup problem (missing kotlinc/junit4) or compile/test failure

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Pure, dependency-free Kotlin packages to verify this way. Add a directory pair here only
# if the package under it has zero androidx/Media3/Room/Compose/other-app-module imports -
# that is what makes it compilable by a bare kotlinc without the real project classpath.
PURE_MAIN_DIRS=(
  "$REPO_ROOT/app/src/main/kotlin/com/metrolist/music/queue"
)
PURE_TEST_DIRS=(
  "$REPO_ROOT/app/src/test/kotlin/com/metrolist/music/queue"
)

JUNIT_JAR="/usr/share/java/junit4.jar"
HAMCREST_JAR="/usr/share/java/hamcrest-core.jar"

if ! command -v kotlinc >/dev/null 2>&1; then
  echo "ERROR: kotlinc not found. Install it with: apt-get install -y kotlin" >&2
  exit 1
fi
if [[ ! -f "$JUNIT_JAR" || ! -f "$HAMCREST_JAR" ]]; then
  echo "ERROR: JUnit4 not found at $JUNIT_JAR / $HAMCREST_JAR. Install it with: apt-get install -y junit4" >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

file_count=0
for dir in "${PURE_MAIN_DIRS[@]}" "${PURE_TEST_DIRS[@]}"; do
  if [[ ! -d "$dir" ]]; then
    echo "ERROR: expected pure-logic directory not found: $dir" >&2
    exit 1
  fi
  for f in "$dir"/*.kt; do
    cp "$f" "$WORKDIR/"
    file_count=$((file_count + 1))
  done
done
echo "Collected $file_count .kt file(s) for standalone compilation."

# The apt-packaged kotlinc (1.3.31) predates trailing-comma support (Kotlin 1.4+), which
# this project's real Kotlin 2.4.10 toolchain uses freely. Strip trailing commas in these
# throwaway copies only - production files are untouched.
for f in "$WORKDIR"/*.kt; do
  perl -0777 -pi -e 's/,(\s*[\)\]])/$1/g' "$f"
done

CP="$JUNIT_JAR:$HAMCREST_JAR"
echo "Compiling with standalone kotlinc..."
kotlinc -cp "$CP" "$WORKDIR"/*.kt -include-runtime -d "$WORKDIR/pure-logic.jar" 2>&1 \
  | grep -v "^Picked up JAVA_TOOL_OPTIONS" \
  | grep -v "OpenJDK.*deprecated" || true

if [[ ! -f "$WORKDIR/pure-logic.jar" ]]; then
  echo "ERROR: compilation failed, see output above." >&2
  exit 1
fi

test_classes=()
for dir in "${PURE_TEST_DIRS[@]}"; do
  pkg="$(sed -n "s#.*/src/test/kotlin/\(.*\)#\1#p" <<< "$dir" | tr '/' '.')"
  for f in "$dir"/*Test.kt; do
    class_name="$(basename "$f" .kt)"
    test_classes+=("$pkg.$class_name")
  done
done

if [[ ${#test_classes[@]} -eq 0 ]]; then
  echo "ERROR: no *Test.kt classes found under the configured PURE_TEST_DIRS." >&2
  exit 1
fi

echo "Running: ${test_classes[*]}"
java -cp "$WORKDIR/pure-logic.jar:$CP" org.junit.runner.JUnitCore "${test_classes[@]}" 2>&1 \
  | grep -v "^Picked up JAVA_TOOL_OPTIONS"
