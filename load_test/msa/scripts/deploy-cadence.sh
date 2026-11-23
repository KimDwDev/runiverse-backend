#!/usr/bin/env bash
# 운영 배포(upstream/main의 first-parent 병합)마다 그 배포가 바꾼 running-service/src/main 파일을
# 실시간 경로(LIVE) · 끝내기·기록 경로(FINISH) · 나머지로 가른다. 시각은 KST.
# '이 배포에 세션 배포도 필요했나'를 표시하는 근거다 — 기능 변경인지(주석·이름 바꾸기 제외)는 사람이 본다.
# 쓰는 곳: RUNBOOK 「0. 운영 증거」, verification.md 「k6 밖 검증」(배포 분류는 한 달마다).
# 사용: load_test/msa/scripts/deploy-cadence.sh [시작일 YYYY-MM-DD, 기본 2026-09-01]
#   REF   볼 브랜치(기본 upstream/main — 조직 저장소를 가리키는 원격, 이름이 다르면 REF로 바꾼다). 먼저 그 원격을 fetch한다. 이 스크립트는 읽기만 한다.
set -Eeuo pipefail
# 어디서 불러도 저장소 루트 기준으로 돈다
ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
cd "$ROOT"

SINCE="${1:-2026-09-01}"
REF="${REF:-upstream/main}"
P=running-service/src/main/java/com/runiverse/running_service
# 실시간 경로 — 연결 · 위치 · 진행률 · 콤보 · 상태
LIVE="$P/presentation/running/websocket|$P/infrastructure/websocket|$P/infrastructure/redis/running|$P/application/running/command/(location|progress|session|status|combo)|$P/application/running/common/LiveRunningStatusChanger"
# 끝내기 · 기록 경로 — 확정 · 트랙 분석 · 구간 · 기록 저장
FINISH="$P/application/running/common/(RunningFinisher|Track|Split|Route|Polyline|Calorie|Boundary|GoalCheck|FilteredTrack|RunningCombo|RunningFinish)|$P/application/running/command/(finish|forcefinish)|$P/infrastructure/persistence/running/(RunningRecord|RunningSegment|RunningSplit|RunningCombo)|$P/domain/running/(record|metric|combo)"

TZ=Asia/Seoul git log "$REF" --first-parent --merges --reverse --since="$SINCE" \
    --date=format-local:'%Y-%m-%d %H:%M' --format='%h|%ad|%s' |
while IFS='|' read -r hash when subject; do
  pr=$(grep -o '#[0-9]*' <<<"$subject" | head -n 1 || true)
  # 병합의 첫 부모와 비교한다 — 그 배포가 운영에 새로 올린 변경만 센다
  files=$(git diff --name-only "${hash}^1" "$hash" -- running-service/src/main)
  total=$(grep -c . <<<"$files" || true)
  live=$(grep -E -c "$LIVE" <<<"$files" || true)
  finish=$(grep -E -c "$FINISH" <<<"$files" || true)
  echo "$when KST $hash ${pr:--} main_files=$total live=$live finish/record=$finish other=$((total - live - finish))"
  grep -E "$LIVE" <<<"$files" | sed 's#.*running_service/#   LIVE: #' || true
  grep -E "$FINISH" <<<"$files" | sed 's#.*running_service/#   FINISH: #' || true
done
