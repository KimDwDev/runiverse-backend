# 러닝 세션 서버

명단이 정해진 뒤부터 결과까지 러닝을 맡을 서버다. 지금은 메인 서버(running-service) 안에서 도는 러닝 세션을 떼어 낸다. 이 문서는 입구다: 무엇을 왜 나누는지 한 줄씩 적고, 단계가 어디까지 왔는지, 다음 작업, 지금 막힌 것을 둔다. 단계 진행 상태는 이 문서에만 둔다.

> 상태: 계획 — 설계는 11-24에 정하고 11-25에 다듬었다(D-82까지). 0단계 운영 증거는 11-25에 ASG · Kibana · Grafana 몫을 모았고 나머지는 「지금 막힌 것」에 있다. 만든 코드와 k6 회차는 아직 없다.

| 무엇 | 어디 |
|---|---|
| 왜 나누는가 — 근거 · 반대 근거 · 깨지는 조건 | [rationale.md](rationale.md) |
| 메인과의 구조 — 경계 · 데이터 주인 · 넘기기 · 이벤트 · 동기 호출 · 공개 경로 | [architecture.md](architecture.md) |
| 일관성 — 불변식 · 계정 탈퇴 saga · 대조 | [consistency.md](consistency.md) |
| 러닝 중 동작 — 시작 · 끝내기 · 계획 종료 · 연결 시간 제한 · 앱 재접속 | [runtime.md](runtime.md) |
| 검증 — 비교군 · 시나리오 · 지표 · 판정 | [verification.md](verification.md) |
| 옮기기 — 지금 얽힌 곳 · 선행 수정 · 단계 · 지울 기준 | [migration.md](migration.md) |
| 결정 기록 (범위 `세션` · `메인` · `공통`) | [../decisions.md](../decisions.md) |
| 데이터 · 인프라 / EKS | [../platform.md](../platform.md) · [../eks.md](../eks.md) |
| 실제 이름 · 목표 구성도 | [../README.md](../README.md) 「서비스 이름」 「구성 — 목표」 |
| k6 하네스 · 판정 기준 · 실행 순서 · 결과 | [load_test/msa/](../../../load_test/msa/README.md) — [PREREG](../../../load_test/msa/PREREG.md) · [RUNBOOK](../../../load_test/msa/RUNBOOK.md) · [RESULTS](../../../load_test/msa/RESULTS.md) |
| 공개 계약 — 러닝 WebSocket · 결과 조회 | [api-spec.md](../../api-spec.md) 5 · 6-1 · 6-2 |

> 주소·키·비밀값은 적지 않는다 — Parameter Store 이름까지만.

## 요약

- **맡는 것** — 명단이 정해진 뒤의 러닝: 러닝 WebSocket(위치 · 상대 진행률 · 콤보), 끝내기와 강제 종료, 러닝 기록의 정본인 러닝 DB, 원본 트랙 S3, 끝낸 직후 결과 조회 6-1 · 6-2 — 정함 (D-24, D-26, D-58).
- **왜** — 메인 배포·장애가 진행 중 러닝을 끊지 않게 한다. 앱이라 배포를 밤까지 미룰 수 없고, 러닝은 콤보와 상대 진행률 때문에 중간에 끊기면 꼬인다 — 정함 (D-45). 운영은 앱 1대라 배포마다 러닝 연결이 모두 끊기는 구조이고 옛 인스턴스를 내린 7번 중 5번이 러닝 시간대였지만, 지금 규모에서 실제로 끊긴 러닝은 확인되지 않았다(11-25) → [rationale.md](rationale.md) 「분리 이유 — 운영에서 확인한 것」 「G1 배포 결합」 「근거를 세우는 순서」.
- **경계** — 모집은 메인이 맡는다. 모집 마감 커밋에 메인이 매칭 방을 닫고 방 확정 이벤트로 러닝 방을 넘기고, 그 뒤 대기 · 이탈 · 카운트다운 · 러닝은 세션이 맡는다 — 이탈도 앱이 세션에 직접 보낸다. 메인은 사본으로 7-1 · 기록 카드를 보인다 — 정함 (D-24, D-26, D-71, D-73) → [architecture.md](architecture.md) 「경계 — 생애 단계」 「넘기기」.
- **이벤트** — 서버 사이 이벤트는 방 확정 · 사용자 변경 · 참가자 결과 셋이다 — 정함 (D-72) → [architecture.md](architecture.md) 「이벤트 (후보)」.
- **saga** — 러닝 중 계정 탈퇴 하나뿐이다 — 정함 (D-43), 메인이 지휘하는 오케스트레이션 · 단계 · 세션 호출 3초도 정함 (D-44, D-70) → [consistency.md](consistency.md) 「계정 탈퇴 saga」.
- **끝내기** — 판정(잠금 안: 필터 · 목표 판정 · 임대) → 봉인(잠금 밖, Redis 임대: 분석 · S3 원본 · 날씨) → 확정(잠금 안: 다시 확인 · 저장). 모놀리스(M0의 F1)와 새 엔진이 같다. 목표 도달 판정은 매번 잠금 안이다(F2 버림) — 정함 (D-63, D-79, D-80, D-82) → [runtime.md](runtime.md).
- **검증** — 나눈 효과는 속도가 아니라 격리로 잰다(SC1 · SC9 · SC10). 비교군은 A · A′ · B0 · B2다 — 정함 (D-57, D-64). 기준 숫자는 PREREG 동결 전 초안이다 → [verification.md](verification.md).
- **옮기는 방식** — 지금 코드를 지우지 않고 같은 저장소에 별도 앱 `running-session-server/`를 만들어 → compose · test에서 검증하고 → 별도 저장소로 옮기고 → 진행 중 방이 0인 조용한 시간에 운영 ALB 경로를 돌리고 → 옛 코드를 지운다. 운영 카나리는 없다 — 정함 (D-22, D-47, D-62, D-78) → [migration.md](migration.md) 「방식」.
- **플랫폼** — EKS는 prod 클러스터 하나 · Auto Mode, test는 docker compose다. Kubernetes에만 있는 동작은 필요한 날 띄우는 임시 EKS에서 확인한다 — 정함 (D-75, D-76, D-77) → [../eks.md](../eks.md).
- **지금** — 러닝 세션은 메인 서버 안에서 돈다([../README.md](../README.md) 「구성 — 지금」). 다음은 M0 선행 수정 PR(F1 · F3 · F5–F9, F4는 버림)이다(「다음 작업」).

## 다음 작업

> 11-25 기준. 단계가 바뀌는 PR이 이 절을 같이 고친다.

**M0 선행 수정 PR — F1 · F3 · F5–F9** (D-65, D-66, D-79, D-82, D-83 — F4는 버림, D-84). 고칠 것과 지금 코드는 [migration.md](migration.md) 「선행 수정 F1–F9」, 체크 목록은 「단계 M0–M9」 M0.

1. **브랜치** — `dev`에서 딴다(예: `refactor/pre-split-finish-and-drain`, [git-convention](../../git-convention.md) 「브랜치 규칙」). PR 대상은 `dev`.
2. **순서** — M0 목록 순서: F1 → F3 → F5 · F6 · F7 · F8 → F9 (F4는 버림, D-84). F 하나씩, 운영 코드는 파일 하나씩 바꾼다 (D-66).
3. **F1** — **코드 됨 (11-25, `chore/build-msa`)**: `RunningFinisher`(지휘자 · 봉인) + `RunningFinishSteps`(판정 · 확정 트랜잭션) + `FinishLeaseRedisAdapter`. E2E 통과, 효과 측정(SC9 A vs A′)은 아직. `RunningFinisher`를 판정(잠금) → 봉인(임대: 분석 · S3 · 날씨) → 확정(잠금)으로 나눈다. 임대를 본 `RUNNING_FINISH`는 `RUNNING_TRACK_UNAVAILABLE`로 재전송을 받는다 (D-79, D-80, D-82). 모양은 migration F1 행, 지킬 순서는 [runtime.md](runtime.md)의 끝내기 표.
   - **F3 코드 됨 (11-25)**: `ForceFinishRunningRoomHandler`(알람 확인) + `ForceFinishSteps`(잠금 안 정리) + `ForceFinishRunnerDispatcher`(`forceFinishExecutor` 워커) + 예약 다시 걸기(`ScheduledJob.reschedule`, D-83). 다음은 F5–F9 (F4는 버림, D-84)
4. **F9** — 소켓은 `4500`으로 나눠 닫는다(D-42 · D-65). 드레이너 · SSE 완료는 migration 「선행 조건」 M0 행, 닫는 순서와 시간은 [runtime.md](runtime.md).
5. **같은 PR의 문서** — migration 「단계마다 고칠 문서」 M0 행.
6. **M0 안 다른 일** — 운영 Grafana HikariCP 패널, metrics-convention 예정 지표, 「선행 조건」의 나머지 M0 행, 하네스 v2 → PREREG 동결 → 비교군 A(수정 전 이미지) · A′ 측정. 어느 것을 이 PR에 넣을지는 정하지 않음.

## 진행 단계

4단계 계획(D-17)에 옮기기 단계 M0–M9와 EKS 단계 E0–E7을 얹은 표다. 단계의 뜻 · 할 일 · 끝나는 조건은 [migration.md](migration.md) 「단계 M0–M9」와 [../eks.md](../eks.md) 「도입 단계 E0–E7」, 판정하는 시나리오와 플랫폼 검증은 [verification.md](verification.md), 기준 숫자는 [PREREG](../../../load_test/msa/PREREG.md)에 있다.

| 계획 단계 | 단계 | 하는 일 | 판정 | 상태 |
|---|---|---|---|---|
| 전체 | 설계 | 이유 · 구조 · 일관성 · 검증 · 옮기기 · 플랫폼 문서와 결정 기록 | — | 됨 (11-25, D-82까지) |
| 1 측정 | 0단계 운영 증거 | 운영 배포 때 진행 중이던 러닝과 끊긴 연결을 센다 — ASG · Kibana · Grafana 몫은 11-25에 모았고, 운영 DB 집계 · 대조일 · REST 비율 · CPU 크레딧이 남았다 | G1의 크기 — [RESULTS](../../../load_test/msa/RESULTS.md) 「0. 운영 증거」 | 진행 중 |
| 1 측정 | 하네스 · PREREG | k6 하네스 v2를 만들고 기준을 얼린다 | SC0 관문 | 대기 |
| 1 측정 | M0 기준선 · 선행 수정 (= E0 · E1) | 지금 모놀리스(A)를 잰다. F1 · F3 · F5–F9를 넣는다(D-65, D-66, D-79 — F2 · F4는 버림, D-80 · D-84). metrics-convention의 예정 지표를 코드로 만든다 | SC0 관문, A의 SC1 · SC3 · SC9 · SC10, '지금 (A)' 장 ([verification.md](verification.md) 「판정과 결과」) | 대기 |
| 2 경계 · 새 앱 | M1 별도 앱 뼈대 + 루트 compose (D-78) | 같은 저장소에 `running-session-server/`, compose로 두 앱 + 경로 프록시 | 기존 E2E가 프록시로 통과 | 대기 |
| 2 경계 · 새 앱 | M2 계약 (아웃박스 · CDC · inbox, DLT) | 서비스 사이 이벤트, 러닝 DB 커넥터, DLT · 건너뜀 알람 · 대조 | SC6 · I5 | 대기 |
| 2 경계 · 새 앱 | M3 새 엔진 + 동등성 | 새 엔진을 옛 엔진과 같은 결과로 맞춘다 | 골든 트랙 차이 0 · 두 엔진 E2E | 대기 |
| 2 경계 · 새 앱 | M4 앱 변경 출시 + 최소 버전 (D-73, D-74, D-78) | 확정 뒤 러닝 WS를 쓰는 앱을 두 플랫폼에 내고 최소 버전을 올린다 — 운영 카나리는 없다 | 스토어 출시 · 최소 버전 | 대기 |
| 3 분리 + saga | M5 별도 프로세스 검증 (B2, test) | 세션을 따로 띄워 핵심 시나리오를 돌린다 — 탈퇴 saga 포함 | 'A′ vs B0 · B2' 장 ([verification.md](verification.md) 「판정과 결과」) | 대기 |
| 3 분리 + saga | M6 별도 저장소로 옮기기 | 「저장소를 나눌 때」 | 새 저장소 CI · E2E | 대기 |
| 3 분리 + saga | M7 운영 추출 | 새 저장소 배포물로 ALB 경로를 돌린다 | 되돌리기 연습 · 양쪽 배포의 서로 영향 0 | 대기 |
| 3 분리 + saga | M8 옛 엔진 삭제 | — | 지울 기준 A | 대기 |
| 3 분리 + saga | M9 버림 (D-78) | 모놀리스 안 호스트가 없다 | — | — |
| 4 플랫폼 | E2 Kubernetes 기초 (test는 compose, D-75) | test를 compose로 옮기고, 매니페스트 · 종료 순서는 필요한 날 띄우는 임시 EKS에서 확인한다 (D-76) | 임시 EKS에서 R1 · 파드 둘 E2E · `MultiInstanceE2eTest` | 대기 |
| 4 플랫폼 | E3 같은 이미지를 세 워크로드로 | main-api · session-ws · session-worker — 증거, 관문 아님 | R2 · R3 · R7 · R8 · R9 | 대기 |
| 4 플랫폼 | E4 운영 EKS | 아직 함대만 나눈 모놀리스 | PREREG 「후보 기준」 · I1–I4 | 대기 |
| 4 플랫폼 | E5 코드 · 데이터 분리 | 데이터 분리를 EKS 위에서 한다 | R4 · R5 · R6, B2의 SC3 · SC4 · SC6, I5 | 대기 |
| 4 플랫폼 | E6 AI 서버 EKS | AI 쪽이 준비되고 정하면 | AI 쪽이 정한다 | 대기 |
| 4 플랫폼 | E7 다듬기 | 증거가 요구할 때 | — | 대기 |

- **상태** — `됨` · `진행 중` · `대기`. 단계를 열거나 닫는 PR이 이 표를 같이 고친다. migration.md · eks.md의 `- [ ]`는 할 일 단위이고 단계 상태가 아니다.
- **AI 쪽 단계** — E6는 `runiverse-ai`가 진행한다.
- **판정 칸의 장** — 결과는 세 장으로 낸다: 지금 (A) / 모놀리스에서 고침 (A vs A′) / 분리 (A′ vs B0 · B2) — [verification.md](verification.md) 「판정과 결과」.
- **겹침** — E0 · E1은 M0 안에서 한다. E2는 M1, E3은 M1–M3, E4는 M4 앞뒤, E5는 M1–M7과 겹치고, E7은 M8 뒤다([../eks.md](../eks.md) 「도입 단계 E0–E7」). M9는 버렸다(D-78).

## 지금 막힌 것

M0 PR은 F8 값 하나 말고는 바로 시작할 수 있다. 아래는 그 PR을 마무리해 운영에 내거나, 측정 · M2 · M3 · M4를 열기 전에 풀 것이다. 답은 링크한 문서에서 기다린다.

| 막힌 것 | 막는 단계 | 누가 | 어디 |
|---|---|---|---|
| **F8 힙 비율 값** — 75 / 70 / 65. SC11(J1–J4) 결과로 정하는데 F8은 M0에 들어가, 그 전까지 쓸 값이 필요하다 | M0 PR (F8) | 개발자 | [migration.md](migration.md) 「정하지 않은 것」 4 |
| **운영에서 F9를 거는 방법과 운영 값** — 인스턴스 새로 고침이 컨테이너에 종료 신호를 주고 기다리게 하는 연결, ALB 유휴 시간 제한, 대상 그룹 검사 경로 · 등록 해제 지연 | M0 운영 배포 (F6 · F9) | 개발자 (콘솔 · 시작 템플릿) | [migration.md](migration.md) 「정하지 않은 것」 7, [../platform.md](../platform.md) 「확인할 운영 값」 |
| **앱 재접속 값** — 재연결 간격, 끝내기 재시도, 재전송 묶음, `HEALTH_CHECK` 주기, 1006 처리. 하네스의 클라이언트 모델과 연결 시간 제한 사슬이 쓴다 | PREREG 동결 · SC1–SC3 | 개발자 (노션 프론트 가이드에서 옮겨 적는다) | [runtime.md](runtime.md) |
| **0단계 남은 집계** — 배포 종료 순간 진행 중이던 러닝 · 슬롯 시간 · 대조 기준선 SQL(0-4), 대조일 끊김 · 재연결(0-3), 18–23시 REST 비율 · CPU 크레딧(0-5). 하네스 프로필과 대조 기준선이 쓴다 | PREREG 동결 | 개발자 (SQL · Kibana · Prometheus · CloudWatch) | [RUNBOOK](../../../load_test/msa/RUNBOOK.md) 「0. 운영 증거」 → [RESULTS](../../../load_test/msa/RESULTS.md) 「0. 운영 증거」 |
| **PREREG 동결** — 기준 숫자 · 비교군별 예측 · 「후보 기준」 채택. AG4 기준은 멘토와 정한다 | 첫 측정 회차 | 개발자 (초안 → 리뷰) · 멘토 | [PREREG](../../../load_test/msa/PREREG.md) 「상태」 |
| **앱이 기록 목록에서 6-1 · 6-2를 다시 여는지** — 열면 M7 전에 옛 방 결과를 러닝 DB로 복사한다 | M2 | 프론트 (앱 코드 확인) | [migration.md](migration.md) 「옛 기록」 (D-69) |
| **확정 뒤 이탈 요청 형식** — 러닝 WS 메시지 vs `POST /running-rooms/{runningRoomId}/leave`, 확정된 방에 답할 409 코드 이름 | M3 앞 api-spec | 백엔드 · 프론트 합의 | [architecture.md](architecture.md) 「정하지 않은 것」 9 (D-73) |
| **앱 변경 일정** — 확정 뒤 대기 · 이탈 · 카운트다운을 러닝 WS로 쓰는 앱을 iOS · 안드로이드로 내고 최소 버전을 올린다. 스토어 심사가 있어 서버보다 먼저 잡는다 | M4 (M7 전) | 프론트 · 팀 | [migration.md](migration.md) 「단계 M0–M9」 M4 (D-73, D-74) |

## 저장소를 나눌 때

M6에서 한다 — 운영 추출(M7) 앞에 둔다 — 정함 (D-62). 별도 저장소는 정함 (D-47).

1. **새 저장소를 만든다** — 저장소 · 이미지 · Kafka 계정 이름은 [../README.md](../README.md) 「서비스 이름」 (제안, D-61). 새 저장소가 지킬 규칙은 [migration.md](migration.md) 「새 저장소가 따르는 규칙」.
2. **runtime.md를 옮긴다** — 이 자리에는 새 저장소를 가리키는 한 줄만 남긴다. 이 문서들은 runtime.md를 파일로만 링크하므로 링크가 그대로 산다.
3. **erd의 러닝 DB 장을 옮긴다** — 그 뒤 러닝 DB 스키마의 정본은 새 저장소의 Flyway다 (D-56).
4. **나머지는 이 폴더에 남는다** — README · rationale · architecture · consistency · verification · migration은 메인이 지킬 경계와 상대 서비스의 기록이다(`ai-server/`와 같은 자리).
5. **결정 기록을 가른다** — 세션 안에서만 뜻이 있는 결정(내부 구조 · 라이브러리)은 새 저장소에 `RS-D-01`부터 적고, 서비스 사이 · 플랫폼 결정은 계속 [../decisions.md](../decisions.md)에 `D-` 번호로 적는다.
6. **색인을 같은 PR에서 고친다** — [../README.md](../README.md) 머리말 표 · 「서비스 이름」, CLAUDE.md · 루트 README 문서 색인.

## 정하지 않은 것

1. **서비스 사이 계약(api-spec 14장)이 사는 저장소** — [architecture.md](architecture.md) 「정하지 않은 것」 8.
