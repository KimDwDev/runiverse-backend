# EKS — 앱 워크로드

메인 서버(running-service), 러닝 세션 서버, AI 전용 서버의 앱 워크로드를 EKS에서 어떻게 돌릴지 정리한다. 클러스터·노드·워크로드·계획 종료·배포·비용·도입 단계를 다룬다. 데이터와 인프라(RDS 둘, Redis EC2 둘, Kafka·Connect EC2, S3)는 클러스터 밖에 두고, 클러스터에서 붙는다. EKS를 목표로 두는 것과 앱 워크로드만 올리는 것은 정했다(D-23, D-48). 나머지 선택은 제안이다(D-41).

> 상태: 제안. 클러스터는 아직 없다. E단계 진행 상태는 이 문서에 두지 않고 running-session-server/README 「진행 단계」 한 곳에만 둔다. main-api와 ai-server의 단계도 거기에 둔다.

| 무엇 | 어디 |
|---|---|
| 데이터·인프라 배치, 지금의 배포·비밀값·관측 | [platform.md](platform.md) 「배치 — 지금과 목표」 「지금 (11-24 기준)」 |
| 공개·관리·내부 포트, 역할 플래그 | [running-session-server/migration.md](running-session-server/migration.md) 「선행 조건」 |
| ALB 경로 규칙 | [running-session-server/architecture.md](running-session-server/architecture.md) 「공개 경로」 |
| 앱의 계획 종료 동작, 연결 시간 제한 사슬 | [running-session-server/runtime.md](running-session-server/runtime.md) |
| 플랫폼 검증 R1–R9 | [running-session-server/verification.md](running-session-server/verification.md) 「플랫폼 검증 R1–R9」 |
| 통과 기준 (`K-R*`·`K-SC*`) | [load_test/msa/PREREG.md](../../load_test/msa/PREREG.md) 「시나리오별 기준」 「후보 기준」 |
| 단계 진행 상태 (E0–E7 포함) | [running-session-server/README.md](running-session-server/README.md) 「진행 단계」 |
| 왜 이렇게 골랐나 | [decisions.md](decisions.md) D-23 · D-40 · D-41 · D-42 · D-48 |

> 주소·계정·키는 적지 않는다. Parameter Store 이름까지만 적는다.

- **고치는 규칙** — 이 문서는 덮어쓴다. 무엇을 고르면 decisions.md에 먼저 적는다. 단계를 지나면 해당 절을 운영 사실로 바꾼다. GitOps·인프라 저장소가 생기면 이 문서를 그 저장소로 옮기고 여기에는 링크만 남긴다.

## 클러스터 · 버전

**상태** — 제안 (D-41). 클러스터 수는 정하지 않음.

| 항목 | 값 | 이유 |
|---|---|---|
| 모드 · 버전 | EKS 표준 모드, Kubernetes 1.36, 서울 리전 | KEDA 2.21과 Karpenter LTS 1.14가 1.36까지만 지원한다 (11-24 기준) |
| 업그레이드 정책 | `STANDARD` (연장 지원 끔) | 연장 지원을 켜 두면 업그레이드를 놓쳐도 요금만 늘고 눈에 띄지 않는다 |
| 다음 업그레이드 | 1.37 — KEDA가 지원한 뒤 test에서 먼저 하고, 1.36 표준 지원이 끝나는 2027-08-02 전에 마친다 | |
| 클러스터 | 둘: test를 먼저 만들고 prod를 만든다 | 배우면서 낸 실수(CRD·오퍼레이터 업그레이드)가 진행 중인 러닝에 닿지 않는다. 하나로 줄이는 안은 「비용」과 「정하지 않은 것」 1 |
| 자리 | prod: 지금 운영 VPC에 EKS 서브넷을 더한다. test: test EC2가 있는 개발 VPC에 둔다 | RDS·Redis EC2·Kafka EC2에 피어링 없이 닿는다 |
| 접근 | access entry(`aws-auth`는 쓰지 않음), 공개·사설 엔드포인트와 CIDR 허용 목록, 컨트롤 플레인 감사 로그 | |

- **직접 운영하는 컨트롤러** — Karpenter 1.14 LTS, AWS Load Balancer Controller v3.6.x, Argo CD와 Argo Rollouts 1.9, KEDA 2.21, External Secrets Operator, ExternalDNS, metrics-server, kube-prometheus-stack, k6 Operator(test만). 직접 운영하는 것 자체가 배우는 목표다(D-23).
- **올리지 않는 것** — Strimzi, 클러스터 안 Valkey, CloudNativePG. Kafka·Redis는 EC2에 두고 RDS는 둘만 쓴다(D-48, D-49, D-50). EKS 관리형 Argo CD도 쓰지 않는다(D-41).

## 노드

**상태** — 제안 (D-41). Karpenter와 Auto Mode 중 무엇을 쓸지는 정하지 않음 (「정하지 않은 것」 2).

| 풀 | 올리는 것 | 인스턴스 | 중단 규칙 |
|---|---|---|---|
| 시스템 (관리형 노드 그룹) | Karpenter, CoreDNS, LBC, Argo CD, KEDA, ESO, Prometheus | Graviton `t4g.medium` — prod 2대, test 1대. `CriticalAddonsOnly` taint, AL2023 arm64 | 노드 그룹 업데이트는 낮에만 |
| `apps` (Karpenter) | main-api, session-ws, session-worker | on-demand `m7g`·`c7g`, large–xlarge | `expireAfter: Never`. 예산은 17:30–24:00 KST(UTC `30 8 * * *`부터 6시간 30분)에 `nodes: "0"`, 그 밖에는 `"1"`. 통합은 `WhenEmptyOrUnderutilized`, 15분 뒤 |
| `spot` (Karpenter) | ai-server, CronJob, test 앱 | `m7g`·`c7g`·`m6g`를 섞은 spot, 모자라면 on-demand | |
| `loadgen` (test만) | k6 Operator 실행기 | spot, taint | |

- **앱 노드에는 버스트형 인스턴스를 쓰지 않는다** — run 14에서 서버 CPU가 치솟았다([load_test/RESULTS.md](../../load_test/RESULTS.md) run 14). 원인은 SC9에서 다시 잰다.
- **session-ws는 spot에 두지 않는다** — 슬롯 중에 2분 회수 알림이 오면 재연결이 한꺼번에 몰린다.
- **노드는 낮에만 바꾼다** — AMI 버전을 Git에 고정해 두고, 버전을 바꾸면 낮에 Drift로 노드가 바뀐다(Drift는 예산을 따른다). EC2 예정 이벤트는 중단 큐(SQS + EventBridge)로 미리 받아 노드를 비운다.
- **이미지는 arm64를 담는다** — E1에서 다중 아키텍처 빌드로 바꾼다. 지금 빌드는 [platform.md](platform.md) 「지금 (11-24 기준)」.
- **상태를 쥔 노드 풀은 두지 않는다** — 상태를 가진 것은 모두 클러스터 밖에 있다(D-48).

## 워크로드

**상태** — 제안 (D-41). ai-server는 AI 쪽이 정한다 (「정하지 않은 것」 6).

E3–E4에서는 한 이미지를 역할 플래그로 나눠 세 워크로드로 돌린다. 세션 저장소의 배포물이 나오면(M6 뒤) session-* 두 워크로드는 그 이미지로 바뀐다. 포트와 역할 플래그는 migration 「선행 조건」에 있다.

| 워크로드 | 하는 일 | 복제 | 자원 (메모리 요청 = 한도, CPU 한도 없음) | 배치 |
|---|---|---|---|---|
| `main-api` | 인증·사용자, 매칭·초대·채팅과 SSE, 기록 카드, 기록 목록(7-1), 알림, 메인 예약 작업, inbox 컨슈머 | 2–6 | 500m / 1.5Gi | `apps`. Deployment로 시작해 Argo Rollouts 카나리로 옮긴다 |
| `session-ws` | 러닝 WebSocket, 결과 조회 6-1·6-2(D-58). 예약 작업과 컨슈머는 없다 | 2–8 | 500m / 1.5Gi | `apps` on-demand만. 우선순위 클래스 `runiverse-critical`, 존 분산 `maxSkew 1` `DoNotSchedule`, PDB, ALB 준비 게이트 |
| `session-worker` | `RUNNING_START`·`RUNNING_FORCE_FINISH` 예약 작업(DB 행 점유는 그대로), 러너마다 따로 하는 강제 종료(F3), 넘기기·사용자 생애 이벤트 inbox, 내부 API, 세션 Redis 채널로 ws 파드에 팬아웃 | 2 고정 | 250m / 1Gi | `apps`, PDB `minAvailable 1` |
| `ai-server` | 기록 카드 음악 요청 소비 (Python) | 1–3 | AI 쪽이 정함 | `spot`. KEDA Kafka 지연 + SageMaker 완료 SQS — [ai-server/architecture.md](ai-server/architecture.md) 「실행 환경」 |

- **JVM 옵션은 파드 env로 준다** — `JAVA_TOOL_OPTIONS`에 MaxRAMPercentage와 `-XX:+ExitOnOutOfMemoryError`를 넣는다. 퍼센트 값은 정하지 않음(F8, migration 「정하지 않은 것」). 지금 Dockerfile `ENTRYPOINT`에는 JVM 옵션이 없다.
- **readiness는 공유 의존을 보지 않는다** — db·redis를 빼야 한다(F6). 그대로 두면 Redis 장애 하나로 모든 파드가 준비 안 됨 상태가 되고, ALB가 fail-open으로 넘어간다.
- **session-ws에는 예약 작업을 두지 않는다** — 자주 비우는 파드에 메모리 타이머를 두면 비울 때마다 타이머를 잃고, 복제 수만큼 타이머가 겹친다. 예약 작업 종류별 주인은 architecture 「데이터 주인」에 있다(D-29).
- **방마다 Kubernetes Job을 쓰는 안은 버렸다** — 18:00:00에 수백 방을 초 단위로 시작해야 한다(D-41).
- **ai-server의 병렬도는 파티션 수에 묶인다** — 지금 토픽 파티션은 1이다([kafka.md](kafka.md) 「토픽」).
- **CronJob은 정리 작업에만 쓴다** — 대조, 아웃박스·inbox 정리처럼 분 단위로 도는 일만 CronJob으로 `spot`에 둔다. 초 단위 시작은 session-worker가 맡는다.

## 계획 종료

**상태** — 제안 (D-41, D-42). 이 절에는 파드 스펙 값만 적는다. 앱이 소켓을 닫는 순서와 시간은 [running-session-server/runtime.md](running-session-server/runtime.md)에, 지금 ASG에서 쓸 값(M0의 F9)은 migration 「선행 수정 F1–F9」에 있다.

| 값 | `session-ws` | `main-api` (SSE) | `session-worker` |
|---|---|---|---|
| 배포 | RollingUpdate `maxSurge 1` · `maxUnavailable 0`, `minReadySeconds 10` | 같은 값. E3부터는 Argo Rollouts 카나리 | RollingUpdate `maxSurge 1` · `maxUnavailable 0` |
| PDB | `maxUnavailable 1` | `maxUnavailable 1` | `minAvailable 1` |
| ALB 준비 게이트 | 켬 — 새 파드가 ALB에서 건강해진 뒤에야 다음 옛 파드를 내린다 | 켬 | — (ALB 대상이 아니다) |
| `preStop` | `sleep 15초` — 등록 해제가 모든 ALB 노드에 퍼질 때까지 기존 소켓을 그대로 둔다 | `sleep 15초` | — |
| 앱 종료 | 드레이너가 소켓을 `4500`으로 나눠 닫는다(runtime.md) | 열린 SseEmitter(매칭 스트림, 앱 스트림)를 완료하고 Spring graceful(시간은 [runtime.md](running-session-server/runtime.md)). 앱은 지금 계약대로 다시 연결한다([api-spec.md](../api-spec.md) 5-A, 앱 스트림). 앱에서 할 일은 migration 「선행 조건」에 있다 | 기본값 |
| `terminationGracePeriodSeconds` | 90 | 60 | 기본값 (30) |
| ALB 등록 해제 지연 | 90초 — preStop과 앱 종료 시간(runtime.md)을 합한 것보다 길다. 남은 연결이 없으면 AWS가 일찍 끝낸다 | 45초 | — |
| 대상 그룹 `slow_start` | 30초 — 막 뜬 JVM에 부하가 한꺼번에 몰리지 않게 | — | — |

- **슬롯 시간(17:30–24:00 KST)에는 자발적으로 파드를 내리지 않는다** — Argo CD 동기화 금지 창(main-api·session-*, 핫픽스는 수동 동기화), Karpenter `apps` 예산 `nodes: "0"`, KEDA 축소 안정화가 함께 막는다. 이 시간에 생기는 중단은 계획 밖(`1006`)이고, 앱의 재연결 경로로 복구한다(runtime.md).
- **닫기 코드는 `4500`을 다시 쓴다** — 앱 출시 없이 지금 계약으로 된다(D-42).
- **축소할 때도 계획 종료를 따른다** — KEDA가 파드를 줄이면 위와 같은 순서로 비운다. 선택 사항: 파드마다 `controller.kubernetes.io/pod-deletion-cost`에 연결 수를 넣어 가장 빈 파드부터 내린다.
- **통과 기준** — R1(롤아웃 재시작) · R2(노드 비우기)의 기준은 PREREG `K-R1-1` · `K-R2-1`이다. R7(메인 배포)은 증거이고 관문이 아니다(`K-R7-1`).

## 엣지 · 자동 확장 · 배포

**상태** — 제안 (D-41).

**엣지** — AWS LBC Ingress를 쓰고, 클러스터마다 IngressGroup ALB를 하나 둔다. 설정은 `target-type: ip`, ACM 인증서, `ELBSecurityPolicy-TLS13-1-2-2021-06`, `drop_invalid_header_fields`, 삭제 보호, S3 접근 로그다. idle timeout은 WS 유휴 시간보다 길게 잡는다. 값은 연결 시간 제한 사슬([running-session-server/runtime.md](running-session-server/runtime.md))을 따른다. Route 53 레코드는 ExternalDNS가 쓴다. 지금은 Ingress를 쓰고, Gateway API는 E7에 test에서 시험한다(D-41).

| `group.order` | 규칙 | 보내는 곳 |
|---|---|---|
| -10 | 고정 응답 — 막는 경로(옛 경로, 내부 경로, actuator) | ALB가 바로 응답 |
| 5 | 러닝 세션 경로 — WS, 결과 조회 6-1 · 6-2 (architecture 「공개 경로」) | `session-ws` |
| 10 | 그 밖의 `/api/v1/*` | `main-api` |
| 20 | 운영 도구 UI (Argo CD) | ALB OIDC 뒤 |

경로 목록은 architecture 「공개 경로」 한 곳에만 둔다. 이 표는 규칙의 순서만 정한다.

**자동 확장** — 슬롯 시간표를 미리 알기 때문에 시간에 맞춰 먼저 늘리고, 연결 수는 안전망으로 쓴다. 슬롯은 18:00–22:00 매시 정각이고, 한 슬롯의 방은 모두 같은 초에 시작한다(`match.allowed-*` 설정).

| 워크로드 | 바닥 · 천장 | 트리거 (KEDA) | 줄이기 |
|---|---|---|---|
| `session-ws` | 2 · 8 | cron 17:30–24:00 Asia/Seoul → 3 (존마다 하나) · Prometheus: WS 연결 게이지, 파드당 한계(SC11에서 잰다)의 절반 · CPU 70 (보조) · Prometheus에 닿지 않으면 4 | 안정화 1800초, 600초에 한 파드씩 |
| `main-api` | 2 · 6 | cron 17:30–24:00 → 3 · 요청률 · SSE 연결 게이지 · CPU 70 | 안정화 600초 |
| `session-worker` | 2 고정 | 없음 — 예약 작업은 DB 행 점유로 나눈다 | — |
| `ai-server` | AI 쪽이 정함 | Kafka 컨슈머 지연 · SQS 깊이 | — |
| 노드 (`apps`) | 평소 2대 | 17:30 cron 바닥이 1–2대를 더 띄운다 | 슬롯 시간 밖에서만 |

- **Hikari 대기로는 늘리지 않는다** — 파드를 늘려도 같은 RDS를 쓰는 풀만 늘어난다. 대기가 0보다 크면 알람을 보낸다.
- **VPA는 추천만 받는다** — 자동 모드는 소켓을 든 파드를 내쫓는다.
- **게이지 이름**은 [metrics-convention.md](../metrics-convention.md) 「기본 제공과 직접 만드는 것」의 예정 행을 따른다.
- **R8(독립 확장)** — session-ws만 늘고 main-api는 바닥에 머무는지, 슬롯 시간에 축소가 한 번도 없는지 본다.

**배포**
- **Argo CD app-of-apps** — 별도 GitOps 저장소를 동기화한다. 저장소에는 공유 Helm 차트, 서비스별 values, 환경별 디렉터리를 둔다. sync wave는 CRD → 오퍼레이터 → NodePool·ClusterSecretStore → 앱 순서다. `selfHeal`을 켠다.
- **동기화 금지 창** — AppProject SyncWindow `deny`를 17:30–24:00 KST(UTC `30 8 * * *`부터 6시간 30분)에 main-api와 session-*에 건다. 핫픽스는 수동 동기화로 낸다.
- **같은 digest를 올린다** — CI는 테스트 → 다중 아키텍처 이미지를 바꿀 수 없는 sha 태그로 ECR에 올림 → compose E2E와 `MultiInstanceE2eTest` → test values에 digest 기록 순서로 돈다. prod 승격은 같은 digest를 옮기는 PR로만 한다.
- **main-api 카나리** — Argo Rollouts ALB 가중치를 10% → 분석(5xx 비율, p95, Hikari 대기) → 50% → 100%로 올린다. 분석 값은 A′ 기준선으로 정한다(PREREG 「후보 기준」). 옛 엔진에서 새 엔진으로 넘길 때는 ALB 가중치를 쓰지 않는다 — 엔진은 방 단위로 고른다(D-36).

## IaC · 비밀값 · 신원 · 네트워크 · 관측

**상태** — 제안 (D-41). 추적 방식은 정함 (D-55).

| 영역 | 방식 | 메모 |
|---|---|---|
| IaC | Terraform으로 관리한다: VPC(prod는 지금 VPC를 가져온다), EKS, 관리형 노드 그룹, access entry, 애드온, IAM, Pod Identity 연결, Karpenter 중단 큐, RDS 둘, Redis EC2 둘, Kafka·Connect EC2, S3, ECR, ACM, Route 53, CloudWatch 알람. Argo CD는 Terraform Helm provider로 처음 한 번 설치한다 | 지금 있는 RDS와 EC2는 import로 가져온다. 오래 사는 데이터는 ACK로 만들지 않는다 — CR을 지우면 DB가 함께 지워질 수 있다. 시험일에는 `perf` 워크스페이스로 임시 RDS와 Redis EC2를 만들고 끝나면 지운다 |
| 비밀값 | External Secrets Operator: Parameter Store를 보는 ClusterSecretStore 하나와 서비스마다 ExternalSecret 하나를 두고 `envFrom`으로 넣는다. EKS Secret은 KMS 봉투 암호화 | 서비스마다 자기 경로만 받는다. 지금은 모든 컨테이너가 경로 전체를 받는다(platform 「지금 (11-24 기준)」). 경로 이름은 platform 「설정 · 비밀값」 |
| 신원 | EKS Pod Identity. ServiceAccount마다 IAM 역할 하나(main-api, session-ws, session-worker, ai-server, 각 컨트롤러) | 코드는 바꾸지 않는다 — `S3ClientConfig`와 `SesClientConfig`는 키 값이 비어 있으면 `DefaultCredentialsProvider`를 쓴다. 버킷별 권한은 platform 「S3」 |
| 네트워크 | 공개 서브넷(ALB, NAT), 사설 서브넷(노드, 파드), 격리 서브넷(RDS). prod는 NAT 게이트웨이 하나, test는 fck-nat. S3 게이트웨이 엔드포인트. 네임스페이스마다 기본 거부 NetworkPolicy에 필요한 허용만 더한다: ALB → 공개 포트, 모니터링 → 관리 포트, 상대 서비스 → 내부 포트, 나가는 길은 RDS·Redis EC2·Kafka EC2만 | Redis EC2, Kafka EC2, RDS의 보안 그룹이 노드 보안 그룹을 허용해야 한다(파드 단위 보안 그룹은 나중). 러닝 경로는 인터넷으로 나가지 않는다 — S3는 엔드포인트로 간다 |
| 지표 | kube-prometheus-stack. Prometheus는 3일만 보관한다(KEDA와 알람용). 길게 볼 지표는 모니터링 EC2로 remote_write. 관리 포트에 ServiceMonitor | 클러스터 안 Prometheus를 '앱만'(D-48)에 포함할지는 정하지 않음 (「정하지 않은 것」 4) |
| 로그 | 앱은 stdout에 ECS JSON을 쓰고, Filebeat DaemonSet이 지금의 ES·Kibana로 보낸다 | 지금은 `LOG_DIR` 아래 파일에 쓴다(`logging.file.name`). k8s 프로필에서는 파일 appender를 끈다 |
| 추적 | OTel Java 에이전트 (D-55) | 붙이는 방법과 보낼 곳은 platform 「관측」 |
| 경보 | CloudWatch 알람 → SNS. 클러스터 밖에 둔다 | 클러스터나 Prometheus가 아파도 알림은 나간다. 대상: ALB, RDS(복제 슬롯 지연 포함), NAT |
| 부하 시험 | test 클러스터의 `loadgen` 풀에서 k6 Operator TestRun을 실제 ALB로 보낸다 | prod에는 부하를 넣지 않는다. 클러스터가 생기기 전 측정은 [load_test/msa/](../../load_test/msa/README.md) |
| 정책 | Pod Security Admission `restricted`, `runAsNonRoot`, 읽기 전용 루트에 `/tmp` emptyDir, capability 모두 제거, seccomp `RuntimeDefault`. ECR 태그 불변과 푸시 때 검사. 기반 이미지는 digest로 고정 | Dockerfile은 이미 `USER spring`이다. 기반 이미지 `eclipse-temurin:21-jre`는 지금 태그로만 지정돼 있다. Kyverno는 E7 |

클러스터 밖에 두는 것 전체는 platform 「배치 — 지금과 목표」에 있다.

## 비용

**상태** — 제안 (D-41). 숫자는 추정이다 — 2026년 11월 서울 온디맨드 가격으로, 예산 절약 배치(D-48)를 기준으로 계산했다. 옛 배치로 낸 합계는 D-40에만 남긴다.

**prod**

| 항목 | 가정 | 월 (USD) |
|---|---|---|
| EKS 컨트롤 플레인 | 클러스터 하나, 시간당 $0.10 | 73.00 |
| 시스템 노드 | `t4g.medium` 2대 | 60.74 |
| 앱 노드 | `m7g.large` 2대 | 146.44 |
| 슬롯 시간 증설 | `m7g.large` 1대 × 6.5시간 × 30일 | 19.56 |
| EBS (노드, Prometheus) | gp3 약 140GiB | 12.77 |
| ALB | 1 LCU | 22.27 |
| NAT 게이트웨이 | 1개 + 30GB | 44.84 |
| 공인 IPv4 | 4개 | 14.60 |
| **클러스터 쪽 소계** | | **≈ 394** |
| 서비스 DB (지금 있음) | `db.t4g.medium` Single-AZ + 100GB로 가정 — 실제 등급은 저장소에 없다 | 87.56 |
| 러닝 DB (새로) | `db.t4g.small` Single-AZ + 20GB | 39.85 |
| Redis EC2 둘 (메인은 지금 있음, 세션은 새로) | 각각 `t3.small` + gp3 10GiB로 가정 | 39.78 |
| Kafka·Connect EC2 (지금 있음) | `t3.medium`([kafka.md](kafka.md)) + gp3 30GiB로 가정 | 40.70 |
| S3·ECR·Route 53·CloudWatch·KMS | | 13 |
| 데이터 전송 | | 10 |
| **prod 합계** | | **≈ 625** |

**test · 시험일** — test 데이터는 지금처럼 test EC2(D-10)에 두는 것으로 보고 비용에 넣지 않았다.

| 항목 | 가정 | 월 (USD) |
|---|---|---|
| test 클러스터를 따로 둘 때 (클러스터 둘) | 컨트롤 플레인 73 + 시스템 `t4g.medium` 1대 30.37 + 앱 `m7g.large` spot 2대 평일 14시간 17.31 + EBS 5.47 + fck-nat 3.98 + ALB 18.18 + IPv4 3개 10.95 + 기타 3 | ≈ 162 |
| test를 prod 클러스터의 네임스페이스로 둘 때 (클러스터 하나) | spot 앱 노드 17.31 + EBS 3.65 + 기타 3. test 데이터 EC2와 VPC가 달라 피어링이 필요하다 | ≈ 24 |
| 시험일 | 월 4일 × 10시간: 앱 `m7g.large` 3대, k6 `c7g.xlarge` spot 3대, 임시 RDS 둘, 임시 Redis EC2 둘 | ≈ 27 |

| 합계 | 월 (USD, 추정) |
|---|---|
| 클러스터 둘 | ≈ 814 |
| 클러스터 하나 | ≈ 676 |

- **넣지 않은 것** — SageMaker와 AI DB(AI 쪽 비용이고, AI DB 자리는 platform 「정하지 않은 것」), 모니터링 EC2(지금 있고 크기가 저장소에 없다), ai-server 파드.
- **없어지는 것** — prod를 옮긴 뒤(E4) 지금의 앱 함대와 ALB 하나가 빠진다. `t3.small` 두 대라면 월 약 $60이다. 실제 대수는 platform 「확인할 운영 값」에서 확인한다.
- **prod 순증가** — 클러스터 쪽 ≈ $394 + 러닝 DB ≈ $40 + 세션 Redis ≈ $20 − 없어지는 것 ≈ $60 = **≈ $394**.
- **줄일 수 있는 곳**
  - 1년 Compute Savings Plan — 늘 도는 EC2(시스템·앱 노드, Redis·Kafka EC2, ≈ $283)의 약 25%로, 월 ≈ $70.
  - RDS 예약 인스턴스.
  - prod NAT 게이트웨이를 fck-nat로 바꾸면 월 ≈ $41을 아낀다. 대신 슬롯 시간에 노드를 늘릴 때 이미지·Pod Identity 경로와 메인의 외부 API 호출이 인스턴스 한 대에 걸린다.
  - Auto Mode를 고르면 노드 요금에 약 12%가 더 붙는다(`m7g.large` 한 대에 월 ≈ $8.8이고, spot에도 붙는다).

## 도입 단계 E0–E7

**상태** — 제안 (D-41). 단계 상태는 running-session-server/README 「진행 단계」에만 둔다. 아래 `- [ ]`는 할 일 단위다 — 모두 체크해도 단계 상태는 README에서 정한다. 통과 기준 숫자는 PREREG의 K id로만 가리킨다. 겹치는 M 단계는 표에 적었고, E단계와 M단계는 서로 다른 순서로 끝날 수 있다.

| E | 이름 | 겹치는 M | 나가는 조건 |
|---|---|---|---|
| E0 | 측정 | M0 | SC0 관문(`K-SC0-*`) 통과. 0단계 운영 증거와 비교군 A 기준선이 RESULTS에 있다 |
| E1 | 모놀리스 안 수정 + Kubernetes 준비 | M0 | F2–F9를 했다면 비교군 A′가 PREREG 「후보 기준」의 `K-SC1-5`(채택한 행만)를 넘는다 |
| E2 | test 클러스터 기초 — 지금 이미지를 Deployment 하나로 | M1 | 파드 둘에서 E2E와 `MultiInstanceE2eTest` 통과. R1(`K-R1-1`, 채택하면 `K-R1-2`) 통과 |
| E3 | 같은 이미지를 세 워크로드로 — 증거이며 관문이 아니다 | M1–M3 | R2·R3·R7·R8·R9 결과가 [load_test/msa/RESULTS.md](../../load_test/msa/RESULTS.md) 「회차 색인」에 있다 |
| E4 | 운영 EKS — 아직 함대만 나눈 모놀리스 | M4 전후 | 운영 슬롯 2주가 PREREG 「후보 기준」을 넘는다. 불변식 I1–I4 어긋남 0 |
| E5 | 코드 · 데이터 분리 | M1–M7 | `K-R4-1` · `K-R5-1` · `K-R5-2` · `K-R6-1` 통과. B2 구성 확인 `K-SC3-4` · `K-SC4-5` · `K-SC6-2` · `K-SC6-4` 통과. 대조 누락 0(I5) |
| E6 | AI 서버 EKS | — | AI 쪽이 정한다 |
| E7 | 다듬기 | M8–M9 뒤 | 없음 — 증거가 필요하다고 할 때 한다 |

### E0 측정 (지금 ASG)
M0와 같은 할 일이다 — [running-session-server/migration.md](running-session-server/migration.md) 「단계 M0–M9」 M0에서 체크한다.

### E1 모놀리스 안 수정 + Kubernetes 준비 (지금 ASG)
M0와 같은 할 일이다 — [running-session-server/migration.md](running-session-server/migration.md) 「단계 M0–M9」 M0에서 체크한다. M0 목록에 없는 것:
- [ ] 관리 포트, probe 그룹, 역할 플래그 (migration 「선행 조건」의 포트 · 역할 표)
- [ ] k8s 프로필 — stdout ECS 로그, 파일 appender 끄기
- [ ] 다중 아키텍처(arm64 포함) 이미지 빌드, 기반 이미지 digest 고정
- A′만으로 풀린 근거는 분리 근거에서 뺀다(verification 「판정과 결과」).

### E2 test 클러스터 기초
- [ ] Terraform: 개발 VPC에 EKS 서브넷, EKS 1.36, 시스템 노드 그룹, Pod Identity, Karpenter 중단 큐
- [ ] Argo CD를 부트스트랩하고 컨트롤러를 올린다(Karpenter NodePool, LBC, ExternalDNS, ESO, KEDA, metrics-server, kube-prometheus-stack, k6 Operator)
- [ ] test 데이터는 지금 test EC2에 그대로 둔다. 보안 그룹만 연다
- [ ] 지금 모놀리스 이미지를 Deployment 하나(파드 둘)로 올린다. SSM `docker rm -f` 배포는 걷어 낸다

### E3 같은 이미지를 세 워크로드로
- [ ] main-api, session-ws, session-worker Deployment(역할 플래그)
- [ ] ALB 경로 규칙에 WS와 6-1 · 6-2를 넣는다 (architecture 「공개 경로」, D-58)
- [ ] KEDA ScaledObject, PDB, 준비 게이트, 존 분산, NetworkPolicy, PSA `restricted`
- [ ] main-api Argo Rollouts 카나리
- [ ] R2·R3·R7·R8·R9. SC11로 파드당 연결 한계를 재서 KEDA 임계값을 정한다
- 관문이 아니다 — 나누는 것은 이미 정했다(D-45, D-47). 결과는 AG6(같은 jar 두 함대)의 EKS 쪽 증거로 쓴다.

### E4 운영 EKS — 함대만 나눈 모놀리스
- [ ] Terraform prod: 지금 운영 VPC에 EKS 서브넷을 더하고, 지금 있는 RDS·Redis EC2·Kafka EC2를 import한다
- [ ] 컨트롤러를 설치하고 동기화 금지 창과 Karpenter 예산을 확인한다
- [ ] Route 53 가중치로 옛 ALB에서 새 ALB로 10% → 50% → 100% 옮긴다. 옮기는 시간은 10:00–16:00 KST뿐이다
- [ ] 되돌릴 수 있게 옛 ASG를 2주 동안 켜 둔 뒤, F9 계획 종료로 걷어 낸다
- Redis·Kafka는 옮기지 않는다 — 같은 EC2를 그대로 쓴다(D-48–D-50). 불변식은 consistency 「불변식 I1–I7」.

### E5 코드 · 데이터 분리
- [ ] Terraform: 러닝 DB RDS, 세션 Redis EC2 (platform 「RDS」 「Redis」)
- [ ] 러닝 DB 커넥터를 Kafka EC2의 Connect에 올린다 (platform 「러닝 DB CDC」)
- [ ] ESO로 비밀값을 서비스별로 나눈다. 세션은 ES256 공개 키만 받는다(D-30)
- [ ] NetworkPolicy — 내부 포트에는 허용 목록에 있는 호출만 들어온다 (architecture 「동기 호출 허용 목록」)
- [ ] session-ws와 session-worker를 세션 저장소 이미지로 바꾼다 (M6 뒤, M7과 함께)
- [ ] R4·R5·R6. E3까지의 결과가 나빠지지 않았는지 본다

### E6 AI 서버 EKS
AI 쪽이 준비되고 정하면 한다. `spot` 풀, KEDA Kafka 지연 + SQS, SageMaker·S3용 Pod Identity, arm64 휠을 쓴다. 파티션이 1이라 병렬도도 1이다([kafka.md](kafka.md) 「정하지 않은 것」 1). 실행 환경은 [ai-server/architecture.md](ai-server/architecture.md) 「실행 환경」, 진행 상태는 [running-session-server/README.md](running-session-server/README.md) 「진행 단계」 E6 행.

### E7 다듬기
- [ ] Multi-AZ RDS — 장애 조치 뒤에도 슬롯이 남는지 임시 RDS로 확인한 다음에 켠다([cdc.md](cdc.md) 「정하지 않은 것」 6)
- [ ] session-ws Argo Rollouts 카나리 + WS smoke 분석
- [ ] Kyverno, WAF
- [ ] Gateway API를 test에서 시험한다
- [ ] Compute Savings Plan
- [ ] Kubernetes 1.37 — test에서 먼저 하고, 2027-08-02 전에 끝낸다
- [ ] R1–R9를 분기마다 다시 돌린다

## 정하지 않은 것

1. **클러스터 수** — 둘(test 먼저, 제안)로 할지, prod 하나에 test 네임스페이스를 둘지. 하나로 하면 월 ≈ $138이 덜 든다. 대신 배우면서 낸 CRD·오퍼레이터·버전 업그레이드 실수가 진행 중인 러닝에 닿고, test 파드가 prod 노드와 보안 그룹을 같이 쓴다. test 데이터 EC2와 VPC가 달라 피어링도 필요하다.
2. **Karpenter와 Auto Mode** — Karpenter(D-41 제안)는 `expireAfter: Never`와 슬롯 시간 예산 0으로 노드를 낮에만 바꾼다. 대신 컨트롤러를 직접 올리고 관리해야 한다. Auto Mode는 노드 수명 상한 21일이 강제여서 슬롯 중에 소켓을 든 노드가 비워질 수 있다. 요금 약 12%가 spot에도 붙고, Karpenter와 LBC가 가려진다. 대신 직접 운영할 것이 줄어든다.
3. **test 데이터 면** — D-48의 '앱만'을 test에도 그대로 적용해 지금 test EC2(Kafka·Postgres·Connect, D-10)에 둘지, 클러스터 안 spot에 올릴지. 클러스터 안에 올리면 오퍼레이터를 배울 수 있지만, test 배치가 운영 배치와 달라진다.
4. **클러스터 안 Prometheus** — KEDA의 Prometheus 트리거는 클러스터에서 조회할 수 있는 Prometheus가 있어야 한다. 이것을 '앱만'(D-48)에 들어가는 허용 인프라로 볼지, KEDA가 모니터링 EC2의 Prometheus를 직접 보게 할지.
5. **모니터링 EC2에 닿는 길** — remote_write와 Filebeat가 운영 모니터링 EC2에 닿아야 한다. test 클러스터는 VPC가 달라 닿지 않는다(부하 시험 때는 test VPC에 모니터링 EC2를 따로 띄웠다). 피어링을 할지, test 모니터링을 클러스터 안에 둘지.
6. **ai-server 워크로드의 주인** — 클러스터에 올릴지, 언제 올릴지, 어떤 자원을 쓸지는 AI 쪽이 정한다(ai-server/architecture 「실행 환경」).
