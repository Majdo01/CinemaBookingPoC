#!/usr/bin/env bash
#
# End-to-end check for the Create Screening flow:
#
#   HTTP client -> C# Web API -> gRPC -> Java Spring Boot -> PostgreSQL
#
# What it verifies
#   1. A valid POST is accepted and answered with HTTP 201 + the created id.
#   2. The record exists in PostgreSQL (checked with psql).
#   3. GET /api/screenings returns the created screening.
#   4. Malformed date/time, empty movie title, empty room and missing fields
#      are rejected with HTTP 400 and never persisted.
#   5. With the Java gRPC server stopped, POST and GET answer HTTP 503.
#
# Usage
#   bash scripts/e2e-create-screening.sh
#
# Prerequisites
#   - PostgreSQL running with the cinema_booking database (Flyway creates and
#     seeds the schema when the Java application starts).
#   - .NET SDK and a JDK on PATH; psql is either on PATH or installed under
#     "C:\Program Files\PostgreSQL\<version>\bin".
#   - Ports 5287 (API) and 9090 (gRPC) must be free; the script starts both
#     services itself and stops only the processes it started.
#
# The script only creates rows whose movie title starts with "E2E " and deletes
# exactly those rows again before it exits. Seed data is never touched.

set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_DIR="$ROOT_DIR/CinemaApi"
JAVA_DIR="$ROOT_DIR/CinemaApplication/cinema-application"

API_URL="${API_URL:-http://localhost:5287}"
API_PORT="${API_PORT:-5287}"
GRPC_PORT="${GRPC_PORT:-9090}"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-cinema_booking}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-admin}"

TMP_DIR="$(mktemp -d)"
JAVA_LOG="$TMP_DIR/java.log"
API_LOG="$TMP_DIR/api.log"
JAR="$JAVA_DIR/target/cinema-application-0.0.1-SNAPSHOT.jar"

JAVA_PID=""
API_PID=""
PSQL=""
CREATED_IDS=()
CHECKS=0
FAILURES=0

# ---------------------------------------------------------------- utilities --

record_pass() { CHECKS=$((CHECKS + 1)); printf 'PASS  %s\n' "$1"; }
record_fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); printf 'FAIL  %s\n' "$1"; }

assert_equals() {
  if [ "$1" = "$2" ]; then
    record_pass "$3 ($2)"
  else
    record_fail "$3 (expected '$1', got '$2')"
  fi
}

assert_contains() {
  case "$2" in
    *"$1"*) record_pass "$3" ;;
    *) record_fail "$3 (expected to find '$1' in '$2')" ;;
  esac
}

port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

wait_for_port() {
  local port="$1" seconds="$2" waited=0
  while [ "$waited" -lt "$seconds" ]; do
    port_open "$port" && return 0
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

wait_for_port_closed() {
  local port="$1" seconds="$2" waited=0
  while [ "$waited" -lt "$seconds" ]; do
    port_open "$port" || return 0
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

find_psql() {
  if command -v psql >/dev/null 2>&1; then
    command -v psql
    return 0
  fi

  local candidate
  for candidate in "/c/Program Files/PostgreSQL/"*/bin/psql.exe; do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  return 1
}

sql() {
  PGPASSWORD="$DB_PASSWORD" "$PSQL" \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc "$1" 2>/dev/null
}

STATUS=""
BODY=""
LOCATION=""

request() {
  local method="$1" url="$2" data="${3-}"
  local body_file="$TMP_DIR/body.json"
  local header_file="$TMP_DIR/headers.txt"

  : > "$body_file"
  : > "$header_file"

  if [ -n "$data" ]; then
    STATUS=$(curl -sS -D "$header_file" -o "$body_file" -w '%{http_code}' \
      -X "$method" -H 'Content-Type: application/json' \
      --data "$data" "$url" 2>/dev/null) || STATUS=000
  else
    STATUS=$(curl -sS -D "$header_file" -o "$body_file" -w '%{http_code}' \
      -X "$method" "$url" 2>/dev/null) || STATUS=000
  fi

  BODY="$(cat "$body_file")"
  LOCATION="$(tr -d '\r' < "$header_file" | sed -n 's/^[Ll]ocation: *//p' | head -1)"
}

json_id() {
  printf '%s' "$1" |
    sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' |
    head -1
}

stop_process() {
  local pid="${1-}"
  [ -z "$pid" ] && return 0
  kill -0 "$pid" 2>/dev/null || return 0
  kill "$pid" 2>/dev/null
  local waited=0
  while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt 15 ]; do
    sleep 1
    waited=$((waited + 1))
  done
}

delete_created_rows() {
  [ "${#CREATED_IDS[@]}" -eq 0 ] && return 0
  [ -z "$PSQL" ] && return 0

  local ids
  ids="$(IFS=,; printf '%s' "${CREATED_IDS[*]}")"
  sql "DELETE FROM screenings WHERE id IN ($ids) AND movie_title LIKE 'E2E %'" >/dev/null
  printf 'Cleaned up test screening(s) with id %s\n' "$ids"
}

cleanup() {
  stop_process "$API_PID"
  stop_process "$JAVA_PID"
  delete_created_rows
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

# ------------------------------------------------------------ prerequisites --

printf '== Prerequisites ==\n'

PSQL="$(find_psql)" || {
  printf 'psql was not found. Add it to PATH or install PostgreSQL.\n' >&2
  exit 2
}

command -v curl >/dev/null 2>&1 || { printf 'curl is required.\n' >&2; exit 2; }
command -v dotnet >/dev/null 2>&1 || { printf 'The .NET SDK is required.\n' >&2; exit 2; }
command -v java >/dev/null 2>&1 || { printf 'A JDK is required.\n' >&2; exit 2; }

if ! [ "$(sql 'SELECT 1')" = "1" ]; then
  printf 'Cannot query PostgreSQL at %s:%s (database %s).\n' \
    "$DB_HOST" "$DB_PORT" "$DB_NAME" >&2
  exit 2
fi

if port_open "$API_PORT"; then
  printf 'Port %s is already in use; stop the running Cinema API first.\n' \
    "$API_PORT" >&2
  exit 2
fi

if port_open "$GRPC_PORT"; then
  printf 'Port %s is already in use; stop the running Java application first.\n' \
    "$GRPC_PORT" >&2
  exit 2
fi

printf 'PostgreSQL reachable, ports %s and %s are free.\n' "$API_PORT" "$GRPC_PORT"

# -------------------------------------------------------------- start stack --

printf '\n== Starting the Java application ==\n'

if ! (cd "$JAVA_DIR" && ./mvnw -B -q -DskipTests package); then
  printf 'The Java application could not be built.\n' >&2
  exit 2
fi

if [ ! -f "$JAR" ]; then
  printf 'Expected jar not found: %s\n' "$JAR" >&2
  exit 2
fi

(cd "$JAVA_DIR" && exec java -jar "$JAR") > "$JAVA_LOG" 2>&1 &
JAVA_PID=$!

if ! wait_for_port "$GRPC_PORT" 90; then
  printf 'The Java gRPC server did not start. Log tail:\n' >&2
  tail -30 "$JAVA_LOG" >&2
  exit 2
fi

printf 'Java gRPC server is listening on %s.\n' "$GRPC_PORT"

printf '\n== Starting the C# Web API ==\n'

if ! (cd "$API_DIR" && dotnet build --nologo -v quiet); then
  printf 'The C# Web API could not be built.\n' >&2
  exit 2
fi

(cd "$API_DIR" && exec dotnet run --no-build --urls "$API_URL") > "$API_LOG" 2>&1 &
API_PID=$!

if ! wait_for_port "$API_PORT" 90; then
  printf 'The C# Web API did not start. Log tail:\n' >&2
  tail -30 "$API_LOG" >&2
  exit 2
fi

printf 'C# Web API is listening on %s.\n' "$API_URL"

# --------------------------------------------------------------- happy path --

printf '\n== Happy path ==\n'

MARKER="E2E $(date +%Y%m%d%H%M%S)"
MOVIE="$MARKER Movie"

request POST "$API_URL/api/screenings" \
  "{\"movieTitle\":\"$MOVIE\",\"startTime\":\"2030-06-15T19:30:00\",\"room\":\"Sal E2E\"}"

assert_equals "201" "$STATUS" "POST valid screening returns 201"
assert_equals "/api/screenings" "$LOCATION" "POST advertises the existing collection location"

ID="$(json_id "$BODY")"
if [ -n "$ID" ]; then
  CREATED_IDS+=("$ID")
  record_pass "POST response contains generated id $ID"
else
  record_fail "POST response contains a generated id (body: $BODY)"
fi

assert_contains "$MOVIE" "$BODY" "POST response contains the movie title"
assert_contains "2030-06-15T19:30" "$BODY" "POST response contains the start time"
assert_contains "Sal E2E" "$BODY" "POST response contains the room"

if [ -n "$ID" ]; then
  assert_equals "1" \
    "$(sql "SELECT count(*) FROM screenings WHERE id = $ID AND movie_title = '$MOVIE'")" \
    "PostgreSQL contains the created screening"

  assert_equals "2030-06-15 19:30:00" \
    "$(sql "SELECT start_time FROM screenings WHERE id = $ID")" \
    "PostgreSQL stored the expected start time"
fi

request GET "$API_URL/api/screenings"
assert_equals "200" "$STATUS" "GET /api/screenings returns 200"
assert_contains "$MOVIE" "$BODY" "GET /api/screenings lists the created screening"

# ------------------------------------------------------- validation failures --

printf '\n== Validation failures ==\n'

request POST "$API_URL/api/screenings" \
  "{\"movieTitle\":\"$MARKER Invalid Date\",\"startTime\":\"tomorrow evening\",\"room\":\"Sal E2E\"}"
assert_equals "400" "$STATUS" "Malformed startTime returns 400"
assert_contains "startTime" "$BODY" "Malformed startTime message names the field"

request POST "$API_URL/api/screenings" \
  "{\"movieTitle\":\"$MARKER Out Of Range\",\"startTime\":\"2030-13-45T99:00:00\",\"room\":\"Sal E2E\"}"
assert_equals "400" "$STATUS" "Impossible date returns 400"

request POST "$API_URL/api/screenings" \
  "{\"movieTitle\":\"\",\"startTime\":\"2030-06-15T19:30:00\",\"room\":\"Sal E2E\"}"
assert_equals "400" "$STATUS" "Empty movie title returns 400"
assert_contains "movieTitle" "$BODY" "Empty movie title message names the field"

request POST "$API_URL/api/screenings" \
  "{\"movieTitle\":\"$MARKER Empty Room\",\"startTime\":\"2030-06-15T19:30:00\",\"room\":\"\"}"
assert_equals "400" "$STATUS" "Empty room returns 400"
assert_contains "room" "$BODY" "Empty room message names the field"

request POST "$API_URL/api/screenings" \
  "{\"startTime\":\"2030-06-15T19:30:00\"}"
assert_equals "400" "$STATUS" "Missing fields return 400"

assert_equals "1" \
  "$(sql "SELECT count(*) FROM screenings WHERE movie_title LIKE 'E2E %'")" \
  "Rejected requests created no rows"

# --------------------------------------------------- Java server unavailable --

printf '\n== Java gRPC server unavailable ==\n'

stop_process "$JAVA_PID"
JAVA_PID=""

if wait_for_port_closed "$GRPC_PORT" 30; then
  record_pass "Java gRPC server stopped"
else
  record_fail "Java gRPC server stopped"
fi

request POST "$API_URL/api/screenings" \
  "{\"movieTitle\":\"$MARKER Unavailable\",\"startTime\":\"2030-06-15T19:30:00\",\"room\":\"Sal E2E\"}"
assert_equals "503" "$STATUS" "POST returns 503 while the gRPC server is down"

request GET "$API_URL/api/screenings"
assert_equals "503" "$STATUS" "GET returns 503 while the gRPC server is down"

assert_equals "1" \
  "$(sql "SELECT count(*) FROM screenings WHERE movie_title LIKE 'E2E %'")" \
  "No row was created while the gRPC server was down"

# ------------------------------------------------------------------ summary --

printf '\n== Summary ==\n'
printf '%s checks, %s failure(s)\n' "$CHECKS" "$FAILURES"

if [ "$FAILURES" -ne 0 ]; then
  printf 'End-to-end run FAILED.\n'
  exit 1
fi

printf 'End-to-end run passed.\n'
exit 0
