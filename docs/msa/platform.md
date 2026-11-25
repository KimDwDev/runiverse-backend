# 플랫폼 — 데이터 · 인프라

러닝 세션 서버를 나눈 뒤 데이터 저장소·이벤트 파이프라인·설정·관측이 어디서 돌고, 지금과 무엇이 달라지는지 적는다. 예산 절약 배치(D-48)를 따른다 — Kafka·Redis는 EC2, RDS는 서비스 DB·러닝 DB 둘, EKS는 앱만. 앱 워크로드는 [eks.md](eks.md), 지금 돌고 있는 Kafka·CDC는 [kafka.md](kafka.md)·[cdc.md](cdc.md)가 정본이다. 왜 그렇게 정했는지는 다시 쓰지 않고 [decisions.md](decisions.md) 항목으로 링크한다.

> 상태: 계획 — 「지금 (11-24 기준)」만 운영 사실이다. 나머지 절과 행은 `운영 중` / `정함 (D-xx)` / `제안` / `정하지 않음`을 단다. 만들고 검증한 것은 그 PR에서 kafka.md·cdc.md로 옮기고, 이 문서의 절은 한 줄 링크로 줄인다.

| 무엇 | 어디 |
|---|---|
| 목표 구성도 | [README.md](README.md) 「구성 — 목표」 |
| 서비스 실제 이름 (저장소·이미지·Kafka 계정) | [README.md](README.md) 「서비스 이름」 |
| 테이블·S3 객체·예약 작업 종류의 주인 | [running-session-server/architecture.md](running-session-server/architecture.md) 「데이터 주인」 |
| 서비스 사이 이벤트 후보 | [running-session-server/architecture.md](running-session-server/architecture.md) 「이벤트 (후보)」 |
| 지금 얽힌 코드 | [running-session-server/migration.md](running-session-server/migration.md) 「지금 얽힌 곳 (11-24 기준)」 |
| 앱 워크로드 · 클러스터 · 비용 | [eks.md](eks.md) |
| 단계 진행 상태 | [running-session-server/README.md](running-session-server/README.md) 「진행 단계」 |

> 주소·비밀번호·키·버킷 ARN은 적지 않는다 — Parameter Store 이름까지만.

## 배치 — 지금과 목표

**상태** — 정함 (D-48). 구성도는 README 「구성 — 목표」 한 곳에만 그린다.

| 무엇 | 지금 (11-24) | 목표 | 상태 |
|---|---|---|---|
| 서비스 DB | prod RDS PostgreSQL 18 단일 AZ · test는 Kafka EC2에 직접 설치한 PostgreSQL 17 · dev 로컬 | 그대로 — 메인 사본은 지금 `running_records` · `running_segments`(D-68). 메인만 붙는다 | 정함 (D-24, D-48, D-68) |
| 러닝 DB | 없음 | 새 RDS PostgreSQL — 러닝 세션 서버만 붙는다 | 정함 (D-24, D-48) · 사양 제안 |
| AI DB | AI 쪽 (도표: MySQL) | RDS가 둘뿐이라 자리가 없다 | 정하지 않음 |
| Redis | EC2 한 대, AOF `everysec` — 모든 키·채널 | 메인 Redis(지금 서버) + 세션 Redis(새 EC2), 각 한 대 | 정함 (D-49) |
| Kafka + Connect | 클러스터마다 EC2 한 대(노드 1), Connect 같은 EC2, 커넥터 하나 | 그대로 + 러닝 DB 커넥터 하나(같은 Connect) | 정함 (D-50) · 브로커 셋·Strimzi·MSK 정하지 않음 |
| S3 | 버킷 넷 — 앱 하나가 모두 다룬다 | 같은 버킷, 서비스마다 쓰기·읽기와 IAM을 나눈다 | 제안 |
| SageMaker (음악 생성) | AI 쪽 | AI 쪽 — [ai-server/architecture.md](ai-server/architecture.md) 「실행 환경」 | AI 쪽 |
| 앱 | test EC2 한 대 (SSM 배포) · prod Auto Scaling 그룹(t3.small 한 대 — 11-25, [RESULTS](../../load_test/msa/RESULTS.md) 「0. 운영 증거」) | prod는 EKS 클러스터 하나(Auto Mode) — [eks.md](eks.md) · test는 compose — 「test 환경」 | 정함 (D-23, D-48, D-75, D-76, D-77) · 세부 제안 (D-41) |
| 모니터링 | 다른 VPC의 모니터링 서버 (Prometheus·Grafana·ES·Kibana) | 계속 쓴다 — 붙이는 법은 eks.md | 제안 |

- **데이터·인프라는 클러스터 밖에 둔다** — 이미 운영으로 검증한 자리이고, 노드 교체가 기록·실시간 상태를 건드리지 않는다.

## 지금 (11-24 기준)

**상태** — 운영 중. 2026-11-24에 저장소(워크플로·Dockerfile·설정·코드)로 확인한 사실이다. 파이프라인이 바뀌면 날짜를 바꿔 다시 쓴다.

| 무엇 | 지금 | 설계에 준 영향 |
|---|---|---|
| test 배포 | `dev` push → 단위 테스트 → 이미지 → E2E → SSM으로 **EC2 한 대**에서 `docker rm -f` 뒤 `docker run --network host`, health 확인(5초 × 30회), 실패하면 앞 이미지로 (`backend-ci-cd.yml`) | 배포마다 test의 WS가 SIGKILL로 끊긴다 — 부하 시험 대상으로 쓰지 않는다 |
| prod 배포 | `main` push → `bootJar -x test`(테스트 안 돌림) → ECR → Parameter Store `DEPLOY_IMAGE_URI` → 인스턴스 새로 고침(Rolling, 최소 정상 100%, 최대 200%, 워밍업 600초), 자동 롤백 설정 없음 (`backend-cd-prod.yml`). 시작 템플릿 사용자 데이터는 저장소 밖 | 배포마다 인스턴스가 모두 바뀌어 살아 있는 WS가 한 번씩 끊긴다 — [rationale](running-session-server/rationale.md) 「G1 배포 결합」 |
| 이미지 | Dockerfile 하나, 기반 `eclipse-temurin:21-jre`(태그), `java -jar` — JVM 옵션·HEALTHCHECK 없음. CI는 `linux/amd64`로만 빌드한다(두 배포 워크플로의 `platforms`) | 힙 비율은 [migration](running-session-server/migration.md) 「선행 수정 F1–F9」 F8 |
| 설정 · 비밀값 | 접속값 · 비밀값은 `application.properties`에 `${VAR}`로만 둔다. test 배포는 `/runiverse/test` 아래를 **재귀로 전부** 읽어 이름 끝만 키로 env 파일을 만든다(여러 줄 값 거부 — PEM은 `/runiverse/files/{test\|deploy}/`). prod는 시작 템플릿 사용자 데이터가 `/runiverse/deploy`로 같은 일을 하는 것으로 본다 — 저장소 밖이라 「확인할 운영 값」 | 컨테이너가 그 경로의 비밀값을 모두 받는다 — 「설정 · 비밀값」 |
| 종료 | WS를 미리 닫는 종료 처리(드레이너·종료 훅)가 없다 | [runtime.md](running-session-server/runtime.md), 선행 수정 F9 |
| health | `/actuator/health` 기본 집계 — DB·Redis 포함(진단용). F6로 `/actuator/health/liveness`(`livenessState`) · `/readiness`(`readinessState`) 그룹을 연다 — 둘 다 DB·Redis를 보지 않는다. 관리 포트는 없다. 세 경로 모두 공개 포트에서 인증 없이 열리고(`SecurityConfig`), ALB 리스너 규칙은 `/actuator/health`만 통과시킨다(그룹 경로는 규칙 2에 403) | 대상 그룹 검사 경로를 readiness로 바꿔야 DB·Redis 장애가 인스턴스 교체로 번지지 않는다 — 코드 배포 뒤에 바꾼다(먼저 바꾸면 없는 경로를 검사한다). Redis가 멈추면 기존 health는 15초 넘게 답하지 않는다(E2E 11-25) |
| 메트릭 | Micrometer → `/actuator/prometheus`, 공통 태그 `application=running-service`. Gauge 0개 — WS·SSE 연결 수가 없다 | 연결 게이지는 검증·자동 확장 전에 만든다 |
| 추적 · 로그 상관 | 추적 없음. `RequestIdFilter`가 요청마다 UUID를 새로 만든다(들어온 ID를 읽지 않음). MDC(`requestId`·`userId`)는 HTTP 필터에서만 넣어 WS 메시지·Redis 구독·Kafka 수신 로그에는 없다 | D-55, 「관측」 |
| 로그 수집 | ECS JSON 파일 → Filebeat → 모니터링 서버 ES. test `docker run`에는 로그 디렉터리 마운트가 없다 | EKS에서는 stdout — 「관측」 |
| 메트릭 도메인 | `RequestDomain.DOMAINS`가 모놀리스 도메인으로 고정 | 세션은 자기 목록을 둔다 |
| 경보 | 저장소에 기록된 CloudWatch 경보는 prod 슬롯 지연 하나 ([cdc.md](cdc.md) 「운영 — 상태 보는 법」) | 「관측」 |

## RDS

**상태** — 서비스 DB는 운영 중(바뀌는 것만 정함, D-24). 러닝 DB는 정함 (D-24, D-48), 사양은 제안.

| | 서비스 DB | 러닝 DB |
|---|---|---|
| 엔진 · 크기 | RDS PostgreSQL 18, 단일 AZ, 스토리지 100 GiB(자동 조정) — 그대로 | RDS PostgreSQL 18, `db.t4g.small`, gp3 20 GiB, 단일 AZ — 제안 |
| 붙는 쪽 | 메인 + Debezium | 러닝 세션 서버 + Debezium. 메인은 붙지 않는다 |
| 더하는 것 | 메인 사본 — 지금 `running_records` · `running_segments`를 그대로 쓴다 (D-68, [architecture](running-session-server/architecture.md) 「메인 사본과 반영」). 결과는 지금 `inbox_events`로 거른다 | 세션 테이블 전부([architecture](running-session-server/architecture.md) 「데이터 주인」), `outbox_events`, inbox, `cdc_heartbeats` |
| 스키마 | 손으로 쓴 SQL — 지금처럼 | Flyway — 정함 (D-56). 언제 들일지는 migration 「정하지 않은 것」 |
| 파라미터 그룹 | `runiverse-backend-deploy-postgres18` (논리 복제 켬) | 새 사용자 정의 그룹 — 논리 복제, `rds.force_ssl=1` |
| Multi-AZ | 정하지 않음 — [cdc.md](cdc.md) 「정하지 않은 것」 6 | 같음 |

- **연결 예산** — 세션 인스턴스 수 × Hikari 풀 + Debezium 1 + Flyway·관리 몇 개가 `max_connections` 아래여야 한다. `micro`가 아니라 `small`을 제안하는 이유다. 실제 `max_connections`는 만든 뒤 읽어 「확인할 운영 값」에 적고, 풀 크기는 선행 수정 F5에서 명시한다.
- **세션 계정을 서비스 DB에 만들지 않는다** — 세션이 메인 테이블을 읽는 길을 처음부터 막는다.

## Redis

**상태** — 정함 (D-49): 서비스마다 EC2 한 대, 둘 다 같은 설정. 나누는 근거는 [rationale](running-session-server/rationale.md) 「G2 Redis 연결 · 실패 영역 공유」, 검증은 [verification](running-session-server/verification.md) 「핵심 시나리오 SC1–SC7」(SC4)·「플랫폼 검증 R1–R9」(R4).

| 설정 | 값 | 이유 |
|---|---|---|
| 지속성 | `appendonly yes`, `appendfsync everysec` | 죽어도 러닝 상태가 남는다 — 지금 운영 Redis와 같다 (D-01, [load_test/RESULTS.md](../../load_test/RESULTS.md) 「AOF 적용 후 재검증 (run 18)」) |
| 메모리가 찰 때 | `maxmemory-policy noeviction` (제안) | 트랙을 조용히 지우지 않고 쓰기 오류로 드러낸다 — 지금 운영 값은 확인한다 |
| 대수 | 각 한 대 — 단일 장애점을 받아들인다 | 예산 (D-48). 복제본·자동 장애 조치 없음 |
| 접근 | 보안 그룹으로 자기 서비스만 | 세션이 메인 블랙리스트를 읽을지는 [architecture](running-session-server/architecture.md) 「인증」 |

### 키 · 채널 주인

키·채널의 주인은 여기 한 곳에 둔다. 지금은 모두 한 Redis, 한 리스너 컨테이너(`runningChannelContainer`)다.

| 키 · 채널 (지금 이름) | 지금 쓰는 곳 | 목표 주인 | 상태 |
|---|---|---|---|
| `running:track:{방}:{사용자}`(stream) · `:seen` · `:dist` · `:status` | `RunningTrackRedisAdapter` · `RunningDistanceRedisAdapter` · `LiveRunningStatusRedisAdapter` | 세션 | 정함 (D-49) |
| `running:combo:{방}:dist` · `:pair` · `:seg` | `RunningComboRedisAdapter` | 세션 | 정함 (D-49) |
| 채널 `running:room:{방}` (진행률·콤보·SUPERSEDE) | `RunningProgressRedisAdapter` · `RunningComboUpdateRedisAdapter` · `RunningSupersedeRedisAdapter` → `RunningRoomSubscriber` | 세션 | 정함 (D-49) |
| (새) 연결 닫기 채널 — 사용자 삭제 때 모든 세션 인스턴스가 그 사용자 소켓을 닫는다 | 없음 — 지금 `RunningConnectionCloseListener`는 같은 인스턴스만 닫는다 | 세션 | 제안 |
| 채널 `schedule:job` | `ScheduledJobRedisAdapter` → `ScheduledJobListener` | 메인 — `MATCH_CLOSE`·`RUNNING_READY`·`RUNNING_REMINDER` | 제안 (D-29) |
| (새) 세션 예약 작업 채널 | — | 세션 — `RUNNING_START`·`RUNNING_FORCE_FINISH`·혼자 달리기 청소기·밀린 작업 훑기 | 제안 (D-29) |
| `user:{사용자}:refresh_token` · `user:access_token:blacklist:{토큰}` | `RefreshTokenRedisAdapter` · `AccessTokenRedisAdapter` | 메인 | 정함 (D-49) — 세션의 블랙리스트 읽기는 「인증」 |
| `user:email_verification:*` | `EmailVerificationRedisAdapter` · `EmailVerificationTicketRedisAdapter` | 메인 | 정함 (D-49) |
| `match:cooldown:{사용자}` | `MatchCooldownRedisAdapter` | 메인 | 정함 (D-49) |
| `record-card:draft:{초안}` · `:lock` | `RecordCardDraftRedisAdapter` | 메인 | 정함 (D-49) |
| 채널 `match:room:{방}` · `match:stream:supersede` · `notification:user` | `MatchEventRedisAdapter` · `MatchSupersedeRedisAdapter` · `UserNotificationRedisAdapter` | 메인 | 정함 (D-49) |

- **pub/sub은 서비스를 넘지 않는다** — 채널은 그 서비스 Redis에만 있다. 서비스 사이는 Kafka다([README.md](README.md) 「통신 — 어디로 무엇이 지나가는가」). 새 채널도 같은 인스턴스 갈아타기·다른 인스턴스 재연결·늦은 종료를 따진다 — 레이어 규칙 ([docs/architecture.md](../architecture.md)) 「다중 인스턴스」.
- **세션 키·채널에는 접두어를 붙인다**(이름은 「새 이름」) — 세션 Redis는 dev compose부터 따로 두지만(D-78) 옛 엔진의 `running:*`와 이름이 겹치지 않게 한다. 세션은 자기 연결 팩토리와 리스너 컨테이너를 쓴다 — [migration](running-session-server/migration.md) 「원격처럼」.
- **키를 옮기지 않는다** — 전환은 진행 중 방이 0인 조용한 시간에 하고(D-78) 새 엔진 방은 처음부터 세션 Redis에 쓴다. 옛 엔진 방의 `running:*`는 방과 함께 끝나고 TTL(`running-track.ttl=7h`)로 사라진다. 옛 엔진을 지우기 전(M8)에 메인 Redis에 `running:*`가 남지 않았는지 본다.
- **Redis 서버를 바꿔 끼울 때는 SQL로 활성 방 0을 확인한 때에만 한다** — 매칭 마지막 슬롯(22:00)의 강제 종료는 04:00(`match.force-finish-offset=6h`)이지만 초대 방은 23:59까지 시작해 05:59까지 갈 수 있다(`OpenInviteRoomHandler`) — 시간대로 정하지 않는다. 메인 Redis는 데이터를 옮긴다(AOF·RDB 복사) — 비우면 리프레시 토큰이 사라져 다시 로그인해야 하고, 블랙리스트가 사라져 폐기한 토큰이 만료까지 통한다.
- **해시 태그는 붙이지 않는다** — 클러스터 모드로 갈 때만 필요하다(트랙 Lua가 키 둘을 쓴다, D-49).

## 러닝 DB CDC

**상태** — 정함 (D-24: 두 번째 커넥터·슬롯). 절차는 [cdc.md](cdc.md) 「새 환경에 CDC를 붙이는 순서」를 그대로 따르고, 아래는 **서비스 DB 커넥터와 다른 것만** 적는다. 만들고 검증하면 cdc.md 「환경」 표에 열을 더하고 이 절은 링크로 줄인다. 검증은 [verification](running-session-server/verification.md)의 SC6(결과 복제)·R5(브로커·Connect 정지).

| 단계 | 다른 것 |
|---|---|
| 1. DB | 러닝 DB용 파라미터 그룹을 새로 만든다(`rds.logical_replication=1`, 바꿔 끼운 뒤 재부팅). `outbox_events`·`cdc_heartbeats`·publication·아웃박스 CHECK는 Flyway가 만든다 — 손 SQL 단계가 없는 대신, publication은 테이블 주인만 만들므로 Flyway를 도는 역할이 테이블 주인이어야 한다. `debezium` 계정 권한은 cdc.md와 같다. WAL 상한은 디스크에 맞춰 서비스 DB(10GB / 100 GiB)보다 낮춘다 |
| 2. Kafka | 같은 Connect 워커라 계정 `{env}.debezium`·Connect 내부 토픽·그룹은 그대로다. 결과 토픽과 새 heartbeat 토픽의 `WRITE` `DESCRIBE`만 더한다 — 이름은 「새 이름」 |
| 3. Connect | 새로 설치하지 않는다. 러닝 DB 비밀번호·주소를 담은 시크릿 파일 하나를 더한다. prod: 러닝 DB 보안 그룹이 Kafka EC2 보안 그룹의 5432를 허용, `sslmode=require`(인증서 확인은 cdc.md 「정하지 않은 것」 7과 같이). test: 메모리부터 본다(「test 환경」) |
| 4. 커넥터 | `infra/kafka/connectors/{env}-outbox.json`을 복사해 이름·`database.dbname`·`slot.name`·시크릿 참조·**`topic.prefix`**를 바꾼다. heartbeat 토픽은 `{env}.debezium-heartbeat.<topic.prefix>`라 prefix가 같으면 서비스 DB 커넥터와 겹친다. prefix를 바꾸면 predicate 패턴(`<prefix>\.public\.outbox_events`)도 바꾸고, 스키마가 `public`이 아니면 `table.include.list`도 바꾼다. 컬럼 매핑(헤더 `id`·key·`topic`·문자열 value)은 그대로 — 메인 inbox 코드를 다시 쓴다 |
| 5. 검증 | cdc.md 「검증 — 테스트가 못 보는 것」 표 + 결과가 메인 사본에 반영되고 대조 누락이 0 ([consistency](running-session-server/consistency.md) 「대조」) |
| 6. 알람 | prod: CloudWatch 슬롯 지연 경보를 러닝 DB에 하나 더(`OldestReplicationSlotLag` → SNS `runiverse-prod-alerts`). test: 없음 — cdc.md 「정하지 않은 것」 3 |
| 7. 앱 | 커넥터를 먼저 붙이고 결과 아웃박스를 켠다(`snapshot.mode=no_data`) — 반대면 그 사이 결과가 나가지 않는다 |

### Connect를 옮길 때 — 같은 슬롯으로 넘기기

**상태** — 제안. Connect를 다른 EC2·클러스터 안으로 옮기거나 브로커를 바꿀 때 커넥터마다 쓴다. 슬롯이 DB에 남아 있어 옮기는 동안 생긴 행도 잃지 않는다. test에서 먼저 해 본다.

1. 브로커가 바뀌면 옛 브로커의 컨슈머 지연을 0으로 만든다.
2. 옛 커넥터를 지운다 — 슬롯은 DB에 남는다(`slot.drop.on.stop` 기본 `false`). 두 커넥터가 한 슬롯을 잡으면 하나는 실패한다.
3. 새 Connect에 같은 `slot.name`·`publication.name`, `snapshot.mode=no_data`로 커넥터를 만든다 — 오프셋이 없으면 슬롯이 확인한 위치부터 읽는다. 마지막 오프셋 기록 뒤의 행은 다시 나갈 수 있고, 받는 쪽 inbox가 거른다.
4. 브로커가 바뀌었으면 받는 쪽 접속 주소·CA를 바꾸고, 옛 브로커는 보관 기간(7일) 동안 남겨 둔다.
5. cdc.md 「검증 — 테스트가 못 보는 것」 표를 다시 돌리고, 옮기는 동안 만든 행이 나갔는지 본다.

## S3

**상태** — 제안. 버킷은 새로 만들지 않는다. 이름 규칙은 지금대로 `s3-runiverse-{용도 복수형}-{local|test|deploy}` — Kafka `{env}`(dev·test·prod)와 섞지 않는다.

| 버킷 (설정 키 `storage.s3.*`) | 지금 앱이 하는 일 | 목표 — 쓰는 쪽 | 목표 — 읽는 쪽 |
|---|---|---|---|
| user-assets (`user-asset-bucket`) | 프로필·배경 업로드 서명, 보기 URL | 메인 | 메인. 세션이 참가자 프로필 URL을 직접 서명할지는 정하지 않음 ([runtime.md](running-session-server/runtime.md)) |
| gps-tracks (`gps-track-bucket`) | 끝낼 때 원본 트랙 저장만(`GpsTrackS3Adapter.save`) — 읽는 코드 없음 | 세션만 | 없음 |
| record-cards (`record-card-bucket`) | 사진 업로드 서명·보기 URL | 앱(메인이 서명) | 메인, AI(`image_key`) |
| 음악 (`music-bucket` = `S3_MUSIC_BUCKET`) | 보기 URL 서명만 | AI | 메인 (8-1 보기 URL) |

- **음악 버킷은 이름이 아직 없다** (D-14) — 기본값이 없어 Parameter Store에 없으면 앱이 뜨지 않는다. 배포 전에 test·deploy에 넣고 앱 역할에 읽기 권한을 준다 — [ai-server/README.md](ai-server/README.md) 「정하지 않은 것」.
- **자격 증명은 코드를 바꾸지 않는다** — `S3ClientConfig`는 정적 키(`S3_ACCESS_KEY_ID`)가 없으면 `DefaultCredentialsProvider`를 쓴다. EC2 역할이든 Pod Identity든 그대로 붙는다. 정적 키는 로컬만.
- **IAM은 서비스 × 버킷마다 준다** — 버킷별 정책(`runiverse-{env}-{버킷 용도}-s3-…-policy`)을 지금처럼 두고, 세션 역할에는 gps-tracks 쓰기만 준다. 메인 역할의 gps-tracks 쓰기는 옛 엔진을 지울 때(M8) 뺀다. EKS에서 역할을 붙이는 법은 [eks.md](eks.md) 「IaC · 비밀값 · 신원 · 네트워크 · 관측」.
- 원본 트랙 key를 고정할지 버전으로 지킬지는 정하지 않음 ([runtime.md](running-session-server/runtime.md)).

## 설정 · 비밀값

**상태** — 제안. Parameter Store가 계속 정본이고, **서비스가 자기 경로만 받는다**는 것만 바뀐다.

| 경로 (제안) | 받는 쪽 | 담는 것 |
|---|---|---|
| `/runiverse/{test\|deploy}/*` | 메인 | 지금 그대로 |
| `/runiverse/session/{test\|deploy}/*` | 세션 | 러닝 DB·세션 Redis 접속, Kafka 계정 `{env}.running-session-server`, gps-tracks 버킷, JWT 검증 키 |
| `/runiverse/files/{test\|deploy}/` | 둘 다 | Kafka CA(PEM) — 지금 그대로 |
| AI | AI 쪽 | — |

- **세션 경로를 지금 경로 밖에 두는 이유** — test 배포는 `/runiverse/test` 아래를 재귀로 읽고 이름 끝만 키로 쓴다. `/runiverse/test/session/REDIS_HOST`를 두면 메인 env 파일에 들어가 메인의 `REDIS_HOST`를 덮어쓴다. EKS로 옮길 때 메인도 자기 경로로 나눌지는 eks.md.
- **세션은 메인의 비밀값을 받지 않는다** — OAuth(Kakao·Google·Apple), FCM, SES, `ADMIN_KEY`, JWT 서명 비밀값. 세션은 JWT 공개 키만 받는다(D-30, 제안). 그 전 임시안 동안은 HS256 비밀값을 검증에만 쓴다 — [architecture](running-session-server/architecture.md) 「인증」.
- **새 변수는 그 변수를 읽는 버전보다 먼저 넣는다** — 기본값 없는 변수는 없으면 기동하지 않는다([kafka.md](kafka.md) 「running-service 접속 설정」).
- **Kafka CA는 파일 경로만 바뀐다** — 지금은 `/app/config/kafka-ca.crt` 읽기 전용 마운트([kafka.md](kafka.md) 「CA 인증서 배포」). 세션에도 같은 클러스터의 CA를 넣고, EKS에서 경로가 바뀌면 `KAFKA_SSL_CA_LOCATION`만 바꾼다.

## 관측

**상태** — 추적은 정함 (D-55), 나머지는 제안. 메트릭 이름은 [metrics-convention.md](../metrics-convention.md) 「기본 제공과 직접 만드는 것」에 `예정`으로 먼저 넣는다 — 여기에 이름을 두지 않는다.

| 무엇 | 목표 | 상태 |
|---|---|---|
| 추적 | OTel Java 에이전트를 실행 때 붙인다(`JAVA_TOOL_OPTIONS`) — 빌드를 바꾸지 않는다. Boot 4.1·Tomcat 11 동작은 test에서 먼저 본다 | 정함 (D-55) |
| 서비스 사이 추적 | 아웃박스 두 곳(서비스 DB·러닝 DB)에 traceparent 컬럼 → 커넥터가 헤더로 싣는다. Redis pub/sub 본문·WS 세션은 직접 넘긴다 | 제안 — 나중 |
| 앱 메트릭 | 세션도 `/actuator/prometheus`, 공통 태그 `application`은 자기 이름. 관리 포트로 연다 — [migration](running-session-server/migration.md) 「선행 조건」. 연결 게이지·끝내기 단계 타이머·inbox 건너뜀·보관 수(D-51) | 제안 |
| 로그 | ECS JSON. EKS에서는 stdout(`logging.structured.format.console=ecs`)만 쓰고 파일을 쓰지 않는다. MDC에 `roomId`·`userId`·WS 세션 ID를 WS·Kafka 경로에서도 넣는다. 세션 도메인 태그는 [logging-convention.md](../logging-convention.md)를 따른다 | 제안 |
| 인프라 | Redis EC2마다 redis_exporter + node_exporter, Kafka 컨슈머 지연, Debezium JMX(`MilliSecondsBehindSource`·`QueueRemainingCapacity`), CloudWatch(ALB·RDS·CPU 크레딧) | 제안 — Kafka 쪽은 [kafka.md](kafka.md) 「정하지 않은 것」 4 |
| 경보 | 클러스터·Prometheus가 죽어도 울려야 하는 것은 CloudWatch → SNS `runiverse-prod-alerts`: RDS 두 곳의 슬롯 지연·CPU·연결 수, ALB 5xx·비정상 대상. inbox 건너뜀·보관 알람은 D-51 | 제안 |

## test 환경

**상태** — 지금 구성은 정함 (D-10) — [cdc.md](cdc.md) 「환경」, [kafka.md](kafka.md) 「클러스터와 환경」. 여기는 러닝 DB와 두 번째 슬롯을 더할 때 따질 것만 적는다. test를 먼저 끝내고 prod로 간다(D-10).

- **러닝 DB 자리는 정하지 않음** — (가) 같은 test PostgreSQL에 데이터베이스 하나 더: 새 EC2가 없고 논리 복제가 이미 켜져 있다. WAL은 클러스터 하나라 슬롯이 둘이어도 상한(5GB)이 디스크를 지킨다. 대신 디스크·메모리를 Kafka와 나누고 절차가 prod(RDS)와 다르다. (나) test RDS: prod와 절차가 같고 비용이 든다.
- **(가)면 먼저 볼 것** — 남은 메모리(Connect 힙 최대 1GB가 이미 올라가 있다), 루트 디스크 남은 양, 두 슬롯의 지연(test는 알람이 없다).
- **세션 Redis는 compose에 따로 둔다** — 정함 (D-75, D-76): 메인 · 세션 Redis 둘.
- **dev는 루트 compose에 Kafka + Debezium을 둔다** — 정함 (D-78). 방 확정 · 참가자 결과가 로컬에서도 실제로 오간다([running-session-server/migration.md](running-session-server/migration.md) 「앱 모양」).
- **앱도 compose로 같은 EC2에 둔다** — 정함 (D-75, D-76). test EC2를 `t3.large`(8GB)로 키우고 메인 · 세션 · Redis 둘을 compose로 더한다(AI 서버는 넣지 않는다). docker compose 플러그인을 설치하고, 키운 뒤 남는 메모리 · 디스크를 확인한다.
- **부하 시험 구성** — [load_test/msa/RUNBOOK.md](../../load_test/msa/RUNBOOK.md) 「1. 준비」.

## 새 이름

**상태** — 제안 · 정하지 않음. 계획 중인 이름은 여기에만 둔다. 만들면 그 PR에서 [kafka.md](kafka.md) 「이름 — 환경 접두어 `{env}`」·「ACL」 표(또는 cdc.md)로 옮기고 이 행을 지운다. 규칙은 지금과 같다 — `KAFKA_ENV` 하나로 만들고, 구분자는 `.`·`-`, ACL은 이름 하나씩(D-04).

| 대상 | 후보 | 상태 |
|---|---|---|
| 세션 Kafka 계정 | `{env}.running-session-server` | 제안 (D-61) |
| 결과 토픽 (세션 → 메인) | `{env}.running.results` — 참가자 결과를 방 ID key로 담아 한 방 안의 순서를 지킨다 | 이름 정하지 않음 · 이벤트는 정함 (D-72) |
| 사용자 변경 토픽 (메인 → 세션) | `{env}.user.changed` — 사용자 ID key | 이름 정하지 않음 · 이벤트는 정함 (D-72) |
| 넘기기 토픽 (메인 → 세션) | `{env}.running-room.confirmed` — 방 ID key | 이름 정하지 않음 · 이벤트는 정함 (D-71) |
| 컨슈머 그룹 | 메인 `{env}.running-service.running-results` · 세션 `{env}.running-session-server.<용도>` | 정하지 않음 |
| DLT | `{topic}.{group}.dlt`(D-51) — `{env}`는 토픽 쪽에 한 번만 | 정하지 않음 (DLT vs 보관 테이블) |
| ACL | `{env}.debezium`: 결과 토픽·새 heartbeat 토픽 `WRITE` `DESCRIBE` / 메인: 결과 토픽 `READ` `DESCRIBE`, 그룹 `READ`, 자기 DLT `WRITE` / 세션: 받는 토픽 `READ` `DESCRIBE`, 그룹 `READ`, 세션 inbox에 DLT를 두면 자기 DLT `WRITE`(D-51) — 그 밖의 직접 발행 권한은 주지 않는다 | 제안 |
| 파티션 수 | 만들 때 정하고 바꾸지 않는다([kafka.md](kafka.md) 「토픽」) | 정하지 않음 |
| 커넥터 · 파일 | `{env}-running-outbox` · `infra/kafka/connectors/{env}-running-outbox.json` | 제안 |
| 슬롯 · publication | `debezium_running_outbox_{env}` · `outbox_publication`(DB가 달라 이름이 같아도 겹치지 않는다) | 제안 |
| `topic.prefix` → heartbeat 토픽 | `running-outbox` → `{env}.debezium-heartbeat.running-outbox` | 제안 |
| Connect 시크릿 파일 · prod 슬롯 경보 | `debezium-{env}-running-db.properties` · `prod-cdc-running-replication-slot-lag` | 제안 |
| 세션 Redis 키·채널 접두어 | `running-session:` — 옛 엔진의 `running:*`와 갈린다 | 제안 |
| 러닝 DB 데이터베이스 · 앱 역할 · 파라미터 그룹 | `running_session` · `runiverse_session` · `runiverse-running-session-deploy-postgres18` | 제안 |
| Parameter Store | `/runiverse/session/{test\|deploy}/*` | 제안 (「설정 · 비밀값」) |

## 확인할 운영 값

**상태** — 11-25 0단계 운영 증거 때 ASG 값만 읽었다. 나머지는 비어 있고, 부하 시험 전에 읽어 날짜와 함께 「값」 칸에 적는다. 주소·키·비밀값은 적지 않고, 시작 템플릿 사용자 데이터는 출력하지 않은 채 항목만 본다.

| 무엇 | 읽는 법 | 쓰는 곳 | 값 (날짜) |
|---|---|---|---|
| ALB 유휴 시간 제한 | `aws elbv2 describe-load-balancer-attributes` | [runtime.md](running-session-server/runtime.md) (연결 시간 제한) | — |
| 대상 그룹 등록 취소 지연 · health 검사 경로·주기·임계 · 분산 방식 | `describe-target-groups` · `describe-target-group-attributes` | 선행 수정 F6·F9, [eks.md](eks.md) 「계획 종료」 | — |
| 리스너 규칙 (health 허용·actuator 403·옛 경로 404) | `describe-rules` | [architecture](running-session-server/architecture.md) 「공개 경로」 | — |
| ASG health 종류(ELB·EC2)·유예·최소/원하는/최대·조정 정책 | `aws autoscaling describe-auto-scaling-groups` · `describe-policies` | F9, 비교군 A | EC2 + ELB, 유예 600초, 원하는 1 · 최소–최대 1–2, 조정 정책은 읽지 않음 (11-25) |
| 배포 기록 (약 6주 보관) | `describe-scaling-activities` | RUNBOOK 「0. 운영 증거」 | [RESULTS](../../load_test/msa/RESULTS.md) 「0. 운영 증거」 (11-25) |
| 시작 템플릿 — `docker run` 정지 시간·로그 마운트, env 파일을 만드는 방식 | `describe-launch-template-versions` | F9, 「설정 · 비밀값」 | — |
| 서비스 DB 클래스 · `max_connections` · `rds.force_ssl` | `aws rds describe-db-instances` · `describe-db-parameters` | 「RDS」 연결 예산, cdc.md 「정하지 않은 것」 8 | — |
| prod Hikari·Tomcat을 덮어쓴 변수가 있는지 (이름만) | Parameter Store 이름 목록 | F5, 비교군 A | — |
| Redis 버전·인스턴스 유형·`maxmemory`·`maxmemory-policy`·AOF 상태 | `CONFIG GET` · `INFO persistence` | 「Redis」 | — |
| test Redis가 어느 EC2에 있는지 (10-15 측정 때는 DB와 같은 EC2) | 콘솔 | 「test 환경」 | — |
| Kafka·Connect EC2 남은 메모리·디스크 (test·prod) | `free` · `df` | 「러닝 DB CDC」 3, 「test 환경」 | — |
| prod Prometheus가 대상을 찾는 방식 · CloudWatch 경보 목록 | 모니터링 서버 설정 · `describe-alarms` | 「관측」 | — |
| test 로그 수집이 이어지는지 (로그 마운트 없음) | Kibana에서 test 로그 마지막 시각 | 「관측」 | — |

## 정하지 않은 것

1. **AI DB 자리** — RDS를 둘로 정해(D-48) 도표의 AI DB(MySQL)가 들어갈 자리가 없다. 후보는 AI 쪽이 운영하는 EC2 MySQL과 AI 쪽 소유 관리형 DB다. AI 쪽 CDC를 쓰면 binlog `ROW`·보관 시간과 Connect 정지 알람이 따라온다. [ai-server/architecture.md](ai-server/architecture.md)가 이 항목을 링크한다.
2. **Kafka 브로커 셋 · Strimzi · MSK** (D-50) — 지금 노드 한 대(복제 1)가 단일 장애점이다. 결과 이벤트가 실제로 흐른 뒤, 파티션을 늘려 소비를 병렬로 해야 할 때, 서버 인증서 SAN이 주소 하나라 노드를 바꾸기 어려울 때 다시 본다. [kafka.md](kafka.md) 「정하지 않은 것」이 이 항목을 링크한다.
3. **Multi-AZ** (두 RDS) — [cdc.md](cdc.md) 「정하지 않은 것」 6. 슬롯이 장애 조치를 넘는지 임시 RDS로 확인한 뒤 정한다.
4. **test 러닝 DB 자리** — 같은 PostgreSQL vs test RDS(「test 환경」). 세션 Redis는 compose로 정했다(D-75, D-76).
5. **러닝 DB WAL 상한 · 스토리지** — 20 GiB에 맞는 `max_slot_wal_keep_size` 값과 스토리지 자동 조정을 켤지.
6. **토픽 셋의 이름 · 파티션 수** (D-72) — 「새 이름」의 후보에서 고른다. 파티션은 만든 뒤 바꾸지 않는다.
7. ~~로컬에서 결과 이벤트 받기~~ — **정했다 → D-78**: 루트 compose에 Kafka + Debezium.
8. **세션이 메인 블랙리스트를 읽을지** — [architecture](running-session-server/architecture.md) 「인증」에서 정한다. 정하면 「Redis」의 접근 행과 키 표의 블랙리스트 행을 고친다.
9. **세션 inbox(방 확정 · 사용자 변경)도 DLT를 둘지** — D-51은 메인이 받는 결과 이벤트만 정했다. 두면 「새 이름」의 세션 ACL에 자기 DLT `WRITE`가 붙는다.
