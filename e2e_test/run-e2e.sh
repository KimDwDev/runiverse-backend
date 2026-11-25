#!/usr/bin/env bash
# 배포될 도커 이미지를 그대로 띄우고 E2E를 돌린 뒤 반드시 정리한다.
# 로컬과 GitHub Actions가 같은 경로를 타도록 양쪽 모두 이 스크립트만 부른다.
set -Eeuo pipefail
E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${E2E_DIR}/compose.yml"
# 래퍼는 새로 만들지 않고 running-service 것을 빌려 쓴다
GRADLEW="$(dirname "${E2E_DIR}")/running-service/gradlew"
# compose의 ${E2E_IMAGE} 치환은 셸 환경변수만 읽는다 (.env.test는 컨테이너 안으로만 들어간다).
# CI는 워크플로가 ECR 태그를 넣어주고, 로컬은 직접 빌드한 기본 태그로 떨어진다.
export E2E_IMAGE="${E2E_IMAGE:-runiverse-backend:e2e}"
# 테스트 코드가 읽을 값 — 여기서 한 번만 정하고 아래 헬스체크와 공유한다
export E2E_BASE_URL="${E2E_BASE_URL:-http://localhost:8080/api/v1}"
export E2E_APP_CONTAINER="${E2E_APP_CONTAINER:-runiverse-e2e-app}"
# true면 두 번째 인스턴스(8081)를 띄워 MultiInstanceE2eTest까지 돌린다 — 기본은 한 대
readonly MULTI_INSTANCE="${E2E_MULTI_INSTANCE:-false}"
readonly SECOND_BASE_URL="http://localhost:8081/api/v1"
readonly STARTUP_RETRIES=60
readonly STARTUP_INTERVAL=2
compose() {
  docker compose --file "${COMPOSE_FILE}" "$@"
}

# 앱의 health가 응답할 때까지 기다린다 — 컨테이너가 이미 죽었으면 더 기다리지 않는다
wait_for_app() {
  local health_url="$1/actuator/health" container="$2"
  echo "===== 앱 기동 대기 (${health_url}) ====="
  for attempt in $(seq 1 "${STARTUP_RETRIES}"); do
    if curl --fail --silent --output /dev/null "${health_url}"; then
      echo "앱이 응답합니다. (${attempt}회차)"
      return 0
    fi
    if [ "$(docker inspect --format '{{.State.Running}}' "${container}" 2>/dev/null)" != "true" ]; then
      echo "앱 컨테이너가 실행 중이 아닙니다. (${container})" >&2
      exit 1
    fi
    sleep "${STARTUP_INTERVAL}"
  done
  echo "앱이 ${STARTUP_RETRIES}회 시도 안에 기동되지 않았습니다. (${container})" >&2
  exit 1
}
# 실패해도 컨테이너를 남기지 않되, 지우기 전에 원인 파악용 로그를 뽑는다
cleanup() {
  local status=$?
  if [ "${status}" -ne 0 ]; then
    echo "===== 실패 — 앱 로그 (마지막 200줄) =====" >&2
    compose logs --tail 200 app >&2 || true
    if [ "${MULTI_INSTANCE}" = "true" ]; then
      echo "===== 실패 — 두 번째 인스턴스 로그 (마지막 200줄) =====" >&2
      compose --profile multi logs --tail 200 app2 >&2 || true
    fi
  fi
  compose --profile multi down --volumes --remove-orphans >/dev/null 2>&1 || true
  # 정리 결과가 아니라 원래 실패 원인의 종료 코드를 돌려줘야 CI가 빨간불이 된다
  exit "${status}"
}
trap cleanup EXIT
echo "===== E2E 스택 기동 (${E2E_IMAGE}) ====="
# --wait: postgres·redis의 healthcheck가 통과할 때까지 블로킹한다
compose up --detach --wait
# 앱 이미지(temurin jre)에 curl이 있다고 보장할 수 없어 준비 확인은 호스트에서 한다
wait_for_app "${E2E_BASE_URL}" "${E2E_APP_CONTAINER}"
# 두 번째 인스턴스는 첫 인스턴스가 스키마를 만든 뒤에 띄운다 — 기동 때 예약 복구가 테이블을 읽는다
if [ "${MULTI_INSTANCE}" = "true" ]; then
  echo "===== 두 번째 인스턴스 기동 ====="
  compose --profile multi up --detach --no-deps app2
  wait_for_app "${SECOND_BASE_URL}" "runiverse-e2e-app2"
  export E2E_SECOND_BASE_URL="${SECOND_BASE_URL}"
fi
# 운영처럼 앱 버전 행은 SQL로 넣는다 — 테이블은 앱이 기동하며 만든다.
# 실패를 삼키면 테스트가 엉뚱한 500으로 깨지므로 SQL 오류에서 바로 멈춘다
echo "===== 앱 버전 시드 ====="
compose exec -T postgres psql --quiet --username=runiverse --dbname=runiverse_e2e \
  --set ON_ERROR_STOP=1 --file=- < "${E2E_DIR}/sql/app-versions.sql"
echo "===== E2E 테스트 실행 ====="
"${GRADLEW}" --project-dir "${E2E_DIR}" test --no-daemon "$@"
