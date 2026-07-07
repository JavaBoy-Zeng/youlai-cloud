#!/usr/bin/env bash
set -euo pipefail

FLOWABLE_VERSION="${FLOWABLE_VERSION:-7.2.0}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MYSQL_DATABASE="${MYSQL_DATABASE:-youlai_flowable}"
M2_REPO="${M2_REPO:-$HOME/.m2/repository}"

if [[ -z "$MYSQL_PASSWORD" ]]; then
  echo "MYSQL_PASSWORD is required" >&2
  exit 1
fi

tmp_sql="$(mktemp -t youlai-flowable-engine-create.XXXXXX.sql)"
trap 'rm -f "$tmp_sql"' EXIT

extract_sql() {
  local jar_path="$1"
  local resource_path="$2"

  if [[ ! -f "$jar_path" ]]; then
    echo "Missing Flowable jar: $jar_path" >&2
    exit 1
  fi

  unzip -p "$jar_path" "$resource_path" >> "$tmp_sql"
  printf '\n' >> "$tmp_sql"
}

{
  echo "CREATE DATABASE IF NOT EXISTS \`$MYSQL_DATABASE\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  echo "USE \`$MYSQL_DATABASE\`;"
} > "$tmp_sql"

extract_sql \
  "$M2_REPO/org/flowable/flowable-engine-common/$FLOWABLE_VERSION/flowable-engine-common-$FLOWABLE_VERSION.jar" \
  "org/flowable/common/db/create/flowable.mysql.create.common.sql"

extract_sql \
  "$M2_REPO/org/flowable/flowable-engine/$FLOWABLE_VERSION/flowable-engine-$FLOWABLE_VERSION.jar" \
  "org/flowable/db/create/flowable.mysql.create.engine.sql"

extract_sql \
  "$M2_REPO/org/flowable/flowable-engine/$FLOWABLE_VERSION/flowable-engine-$FLOWABLE_VERSION.jar" \
  "org/flowable/db/create/flowable.mysql.create.history.sql"

extract_sql \
  "$M2_REPO/org/flowable/flowable-event-registry/$FLOWABLE_VERSION/flowable-event-registry-$FLOWABLE_VERSION.jar" \
  "org/flowable/eventregistry/db/create/flowable.mysql.create.eventregistry.sql"

extract_sql \
  "$M2_REPO/org/flowable/flowable-idm-engine/$FLOWABLE_VERSION/flowable-idm-engine-$FLOWABLE_VERSION.jar" \
  "org/flowable/idm/db/create/flowable.mysql.create.identity.sql"

MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  -h "$MYSQL_HOST" \
  -P "$MYSQL_PORT" \
  -u "$MYSQL_USER" \
  < "$tmp_sql"

echo "Flowable $FLOWABLE_VERSION engine tables created in $MYSQL_DATABASE"
