# 러닝 세션 서버

명단이 정해진 뒤부터 결과까지 러닝을 맡을 서버다. 지금은 메인 서버(running-service) 안에서 도는 러닝 세션을 떼어 낸다. 이 문서는 입구다: 무엇을 왜 나누는지 한 줄씩 적고, 단계가 어디까지 왔는지와 지금 막힌 것을 둔다. 단계 진행 상태는 이 문서에만 둔다.

> 상태: 계획 — 11-24에 설계를 정했다. 만든 코드도, 잰 결과도 아직 없다.

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
- **왜** — 메인 배포·장애가 진행 중 러닝을 끊지 않게 한다. 앱이라 배포를 밤까지 미룰 수 없고, 러닝은 콤보와 상대 진행률 때문에 중간에 끊기면 꼬인다 — 정함 (D-45) → [rationale.md](rationale.md) 「분리 이유 — 운영에서 확인한 것」.
- **경계** — 명단이 정해질 때까지 모집은 메인이 맡고, 그 뒤 러닝은 세션이 맡는다. 메인은 사본으로 7-1 · 기록 카드를 보인다 — 정함 (D-26, D-24). 넘기는 방식은 정하지 않음 → [architecture.md](architecture.md) 「경계 — 생애 단계」 「넘기기」.
- **saga** — 러닝 중 계정 탈퇴 하나뿐이다 — 정함 (D-43), 단계는 제안 (D-44) → [consistency.md](consistency.md) 「계정 탈퇴 saga」.
- **옮기는 방식** — 기존 코드를 지우지 않고 모놀리스 안에 새로 만들고 → 검증하고 → 별도 저장소로 옮기고 → 새것이 옛 코드 없이 돌면 옛 코드를 지운다 — 정함 (D-22, D-47) → [migration.md](migration.md) 「방식」.
- **지금** — 러닝 세션은 메인 서버 안에서 돈다([../README.md](../README.md) 「구성 — 지금」). 다음은 1단계 측정이다 — 운영 증거부터 모은다(「지금 막힌 것」).

## 진행 단계

4단계 계획(D-17)에 옮기기 단계 M0–M9와 EKS 단계 E0–E7을 얹은 표다. 단계의 뜻 · 할 일 · 끝나는 조건은 [migration.md](migration.md) 「단계 M0–M9」와 [../eks.md](../eks.md) 「도입 단계 E0–E7」, 판정하는 시나리오와 플랫폼 검증은 [verification.md](verification.md), 기준 숫자는 [PREREG](../../../load_test/msa/PREREG.md)에 있다.

| 계획 단계 | 단계 | 하는 일 | 판정 | 상태 |
|---|---|---|---|---|
| 전체 | 설계 | 이유 · 구조 · 일관성 · 검증 · 옮기기 · 플랫폼 문서와 결정 기록 | — | 됨 (11-24) |
| 1 측정 | 0단계 운영 증거 | 운영 배포 때 진행 중이던 러닝과 끊긴 연결을 센다 | G1의 크기 | 대기 |
| 1 측정 | 하네스 · PREREG | k6 하네스 v2를 만들고 기준을 얼린다 | SC0 관문 | 대기 |
| 1 측정 | M0 기준선 · 선행 수정 (= E0 · E1) | 지금 모놀리스(A)를 잰다. F3 · F5–F9를 넣는다(D-65, D-66 — 끝내기 F1 · F2는 M3, F4는 승인 대기). metrics-convention의 예정 지표를 코드로 만든다 | SC0 관문, A의 SC1 · SC3 · SC9 · SC10, '지금 (A)' 장 ([verification.md](verification.md) 「판정과 결과」) | 대기 |
| 2 경계 · 새 모듈 | M1 모듈 뼈대 (트래픽 0%) | 서브프로젝트 · 스위치 · 경계 검사 | E2E 그대로 · 경계 검사 위반 0 | 대기 |
| 2 경계 · 새 모듈 | M2 계약 (아웃박스 · CDC · inbox, DLT) | 서비스 사이 이벤트, 러닝 DB 커넥터, DLT · 건너뜀 알람 · 대조 | SC6 · I5 | 대기 |
| 2 경계 · 새 모듈 | M3 새 엔진 + 동등성 | 새 엔진을 옛 엔진과 같은 결과로 맞춘다 | 골든 트랙 차이 0 · 두 엔진 E2E | 대기 |
| 2 경계 · 새 모듈 | M4 방 단위 카나리 (모놀리스 안) | 운영에서 방마다 엔진을 골라 새 엔진 비율을 올린다 | I1 · I5 | 대기 |
| 3 분리 + saga | M5 별도 프로세스 검증 (B2, test) | 세션을 따로 띄워 핵심 시나리오를 돌린다 — 탈퇴 saga 포함 | 'A′ vs B0 · B2' 장 ([verification.md](verification.md) 「판정과 결과」) | 대기 |
| 3 분리 + saga | M6 별도 저장소로 옮기기 | 「저장소를 나눌 때」 | 새 저장소 CI · E2E | 대기 |
| 3 분리 + saga | M7 운영 추출 | 새 저장소 배포물로 ALB 경로를 돌린다 | 되돌리기 연습 · 양쪽 배포의 서로 영향 0 | 대기 |
| 3 분리 + saga | M8 옛 엔진 삭제 | — | 지울 기준 A | 대기 |
| 3 분리 + saga | M9 모놀리스 안 호스트 제거 | — | 지울 기준 B | 대기 |
| 4 플랫폼 | E2 test 클러스터 기초 | 지금 이미지를 Deployment 하나로 — SSM `docker rm -f` 배포를 대신한다 | R1 · 파드 둘에서 E2E · `MultiInstanceE2eTest` | 대기 |
| 4 플랫폼 | E3 같은 이미지를 세 워크로드로 | main-api · session-ws · session-worker — 증거, 관문 아님 | R2 · R3 · R7 · R8 · R9 | 대기 |
| 4 플랫폼 | E4 운영 EKS | 아직 함대만 나눈 모놀리스 | PREREG 「후보 기준」 · I1–I4 | 대기 |
| 4 플랫폼 | E5 코드 · 데이터 분리 | 데이터 분리를 EKS 위에서 한다 | R4 · R5 · R6, B2의 SC3 · SC4 · SC6, I5 | 대기 |
| 4 플랫폼 | E6 AI 서버 EKS | AI 쪽이 준비되고 정하면 | AI 쪽이 정한다 | 대기 |
| 4 플랫폼 | E7 다듬기 | 증거가 요구할 때 | — | 대기 |

- **상태** — `됨` · `진행 중` · `대기`. 단계를 열거나 닫는 PR이 이 표를 같이 고친다. migration.md · eks.md의 `- [ ]`는 할 일 단위이고 단계 상태가 아니다.
- **AI 쪽 단계** — E6는 `runiverse-ai`가 진행한다.
- **판정 칸의 장** — 결과는 세 장으로 낸다: 지금 (A) / 모놀리스에서 고침 (A vs A′) / 분리 (A′ vs B0 · B2) — [verification.md](verification.md) 「판정과 결과」.
- **겹침** — E0 · E1은 M0 안에서 한다. E2는 M1, E3은 M1–M3, E4는 M4 앞뒤, E5는 M1–M7과 겹치고, E7은 M8–M9 뒤다([../eks.md](../eks.md) 「도입 단계 E0–E7」).

## 지금 막힌 것

1단계(측정)를 열기 전에 풀어야 할 것만 적는다. 답은 링크한 문서에서 기다린다.

| 막힌 것 | 누가 | 어디 |
|---|---|---|
| **0단계 운영 증거** — 배포 시각마다 진행 중이던 러닝 · 끊긴 연결 · 재연결 수. ASG 활동 기록은 약 6주만 남아 오래된 배포부터 사라진다 | 개발자 (ASG · Kibana · SQL) | [RUNBOOK](../../../load_test/msa/RUNBOOK.md) 「0. 운영 증거」 → [RESULTS](../../../load_test/msa/RESULTS.md) 「0. 운영 증거」 |
| **앱 재접속 값** — 재연결 간격, 끝내기 재시도, 재전송 묶음, `HEALTH_CHECK` 주기, 1006 처리. 하네스의 클라이언트 모델과 연결 시간 제한 사슬이 쓴다 | 개발자 (노션 프론트 가이드에서 옮겨 적는다) | [runtime.md](runtime.md) |
| **운영 값 확인** — ALB idle timeout, 대상 그룹 검사 경로 · 등록 해제 지연, ASG가 ELB 검사를 쓰는지 | 개발자 (콘솔) | [../platform.md](../platform.md) 「확인할 운영 값」 |
| **PREREG 동결** — 기준 숫자와 비교군별 예측. AG4 기준은 멘토와 정한다 | 개발자 (초안 → 리뷰) | [PREREG](../../../load_test/msa/PREREG.md) 「상태」 |
| **F4 승인** — `app_versions`를 캐시하면 최소 버전 상향이 그만큼 늦게 걸린다 | 개발자 | [migration.md](migration.md) 「선행 수정 F1–F9」 |
| **M0를 여는 제안 결정** — 끝내기 결함을 옛 엔진에서 먼저 고친다 (D-34), k6 v2 비교군 · 시나리오 (D-57) | 개발자 | [../decisions.md](../decisions.md) D-34 · D-57 |

## 저장소를 나눌 때

M6에서 한다 — 운영 추출(M7) 앞에 두는 것은 제안 (D-62). 별도 저장소는 정함 (D-47).

1. **새 저장소를 만든다** — 저장소 · 이미지 · Kafka 계정 이름은 [../README.md](../README.md) 「서비스 이름」 (제안, D-61). 새 저장소가 지킬 규칙은 [migration.md](migration.md) 「새 저장소가 따르는 규칙」.
2. **runtime.md를 옮긴다** — 이 자리에는 새 저장소를 가리키는 한 줄만 남긴다. 이 문서들은 runtime.md를 파일로만 링크하므로 링크가 그대로 산다.
3. **erd의 러닝 DB 장을 옮긴다** — 그 뒤 러닝 DB 스키마의 정본은 새 저장소의 Flyway다 (D-56).
4. **나머지는 이 폴더에 남는다** — README · rationale · architecture · consistency · verification · migration은 메인이 지킬 경계와 상대 서비스의 기록이다(`ai-server/`와 같은 자리).
5. **결정 기록을 가른다** — 세션 안에서만 뜻이 있는 결정(내부 구조 · 라이브러리)은 새 저장소에 `RS-D-01`부터 적고, 서비스 사이 · 플랫폼 결정은 계속 [../decisions.md](../decisions.md)에 `D-` 번호로 적는다.
6. **색인을 같은 PR에서 고친다** — [../README.md](../README.md) 머리말 표 · 「서비스 이름」, CLAUDE.md · 루트 README 문서 색인.

## 정하지 않은 것

1. **서비스 사이 계약(api-spec 14장)이 사는 저장소** — [architecture.md](architecture.md) 「정하지 않은 것」 8.
