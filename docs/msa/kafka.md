# Kafka

서비스 사이 이벤트가 지나가는 Kafka 클러스터의 **지금 구성**과, 그 구성 때문에 개발·운영에서 지켜야 할 것을 적는다. 설치 명령은 적지 않는다 — 구조가 바뀌면 이 문서를 먼저 고친다.

| 무엇 | 어디 |
|---|---|
| 이벤트 계약 (토픽·본문 필드) | [api-spec.md](../api-spec.md) 9-5 |
| 아웃박스 → Kafka 발행 | [cdc.md](cdc.md) |
| 보내고 받는 코드 규칙 | [outbox-inbox.md](outbox-inbox.md) |

> 주소·비밀번호·키 파일은 이 문서에 적지 않는다. 브로커 주소는 환경 변수 `KAFKA_BOOTSTRAP_SERVERS`(SSM)로만 다룬다.

## 한눈에

```text
running-service ─┐
AI 서버 ─────────┼─ SASL_SSL :9092 (TLS + SCRAM, ACL로 권한 확인) ─▶ Kafka EC2 (노드 1대)
Debezium (예정) ─┘                                                  ├ broker
                                                                    └ controller  ← 127.0.0.1:9093
                                                                       (SASL_PLAINTEXT · PLAIN, 같은 EC2 안만)
```

## 클러스터

| 항목 | 값 | 개발에 주는 의미 |
|---|---|---|
| 버전 | Kafka 4.1.2, Java 21 | 멱등 프로듀서는 토픽 `WRITE`만 있으면 된다(클러스터 `IDEMPOTENT_WRITE` 불필요) |
| 방식 | **KRaft 결합 모드** — 한 프로세스가 broker·controller를 겸한다 | 노드가 하나다 (아래 「노드가 하나라서」) |
| 실행 | systemd 서비스 `kafka`, 힙 512 MiB, 실패 시 10초 뒤 재시작 | 재시작하는 동안 모든 클라이언트가 잠깐 끊긴다 — 클라이언트는 재연결·재시도로 견딘다 |
| 저장 | 메시지와 KRaft 메타데이터를 다른 디렉터리에 둔다 | |
| 토픽 자동 생성 | **꺼짐** (`auto.create.topics.enable=false`) | 토픽 이름을 잘못 쓰면 토픽이 생기지 않고 오류가 난다. 새 토픽은 관리자가 만든다 |
| 기본 파티션 · 복제 | 파티션 1, 복제 계수 1, min ISR 1 (내부 토픽 포함) | |
| 기본 보관 | 7일, 세그먼트 1 GiB | 토픽별로 덮어쓰지 않았다 — 지금 토픽은 모두 7일이다 |
| 접근 | bastion을 거친 SSH로만 들어간다 | |

### 환경

| 환경 | 클러스터 | 상태 |
|---|---|---|
| `dev` · `test` | **같은 클러스터를 같이 쓴다** (개발 VPC) | 계정·토픽·ACL 구성 완료. 다른 EC2(테스트 백엔드 서버)에서 붙는 TLS·SASL·송수신은 **`test` 계정으로만** 검증했다 — `dev`는 Kafka EC2 안에서만 확인 |
| `prod` | **따로 있다** (운영 VPC) — 같은 절차로 만들어 구성이 같다(노드 1대·KRaft 결합 모드·리스너·SASL·ACL) | 계정·토픽·ACL 구성 완료. 송수신은 Kafka EC2 안에서만 확인 |

- **CA도 클러스터마다 따로다** — 같은 절차로 각 클러스터에서 CA를 만들었다. 운영 서버에는 운영 CA 인증서, 개발·테스트 서버에는 개발 CA 인증서를 넣는다. 바꿔 넣으면 TLS 검증에서 실패한다.
- 이 문서의 「클러스터」 표와 아래 설정은 두 클러스터에 똑같이 적용된다.

### 노드가 하나라서

- **노드가 내려가면 Kafka 전체가 멈춘다** — 복제본이 없어 넘겨받을 노드가 없다.
  - 요청 이벤트는 사라지지 않는다. 아웃박스 행은 DB에 있고, Debezium은 발행하지 못한 동안 복제 슬롯 위치에 멈춰 있다가 돌아오면 이어서 보낸다. **대신 그동안 Postgres에 WAL이 쌓인다** — 슬롯 지연 알람이 이것을 잡는다([cdc.md](cdc.md)).
  - 완료 이벤트는 AI 서버가 보내지 못한다 — 다시 보내는 책임은 AI 서버에 있다.
- **디스크를 잃으면 보관 중인 메시지를 잃는다** — 복제본이 없다. 아웃박스 행은 DB에 남으므로 요청 이벤트는 다시 보낼 근거가 있다(다시 보내는 절차는 아직 없다).
- 노드를 늘릴 때는 복제 계수·min ISR·내부 토픽 복제 계수를 함께 올린다. 브로커끼리 통신은 이미 `SASL_SSL` 리스너를 쓰도록 되어 있어 TLS 구조는 그대로 간다. 다만 서버 인증서 SAN에 지금 노드 주소 하나뿐이라 **새 노드마다 자기 주소로 인증서를 발급**한다(같은 CA로 서명하면 클라이언트는 그대로).

## 이름 — 환경 접두어 `{env}`

dev·test가 클러스터 하나를 같이 써서, **토픽·컨슈머 그룹·계정 이름 앞에 환경을 붙여** 서로의 이벤트를 아예 보지 못하게 한다.

- `{env}`는 `dev` · `test` · `prod` 중 하나다. 운영은 클러스터가 달라도 같은 규칙으로 `prod`를 붙인다.
- **이벤트 안에 환경 필드를 넣어 걸러 내지 않는다** — 남의 환경 이벤트를 받은 뒤에야 버리게 되고, 거르는 코드를 빠뜨린 쪽은 그대로 처리한다. 토픽이 다르면 애초에 받지 않는다.
- 구분자는 `.`과 `-`만 쓴다 — Kafka는 메트릭 이름에서 `.`과 `_`를 같게 본다.
- running-service는 `KAFKA_ENV` 하나로 모든 이름을 만든다(`KafkaNamingProperties`) — 이름마다 변수를 두지 않는다.

| 대상 | 이름 |
|---|---|
| 요청 토픽 | `{env}.record-card.music.requested` |
| 완료 토픽 | `{env}.record-card.music.generated` |
| running-service 컨슈머 그룹 | `{env}.running-service.record-card-music` |
| AI 서버 컨슈머 그룹 | `{env}.runiverse-ai.record-card-music` |
| running-service 계정 | `{env}.running-service` |
| AI 서버 계정 | `{env}.runiverse-ai` |
| CDC 커넥터 계정 | `{env}.debezium` — test만 있다 (아래 「Debezium을 붙일 때」). Connect가 내부 토픽을 쓸 때도 이 계정 하나를 쓴다 |
| Connect 그룹 | `{env}.debezium-connect` |
| Connect 내부 토픽 | `{env}.debezium-connect.configs` · `.offsets` · `.status` |

- **환경 분리는 검증했다** — dev 계정으로 test 토픽을, test 계정으로 dev 토픽을 조회하면 권한 오류로 끝난다. 계정을 환경마다 나눈 이유가 이것이다: 설정을 잘못 넣은 서버는 접속 단계에서 막힌다.

## 토픽

| 토픽 | 파티션 | 복제 | 보관 | 정리 방식 |
|---|---|---|---|---|
| `{env}.record-card.music.requested` | 1 | 1 | 7일 (기본값) | `delete` (기본값) |
| `{env}.record-card.music.generated` | 1 | 1 | 7일 (기본값) | `delete` (기본값) |
| `{env}.debezium-connect.configs` · `.offsets` · `.status` | 1 | 1 | 키마다 최신 값 | **`compact`** — test·prod 있다(11-17) |
| `{env}.debezium-heartbeat.outbox` | 1 | 1 | **1일** | `delete` — Debezium heartbeat 기록. test·prod 있다(11-17) |

- **Connect 내부 토픽은 `compact`여야 한다** — 커넥터 설정과 "WAL을 어디까지 보냈는지"를 키마다 최신 값으로 남긴다. `delete`면 7일 뒤 지워져 커넥터가 처음부터 다시 시작한다. configs는 파티션이 반드시 1개다.

- **정리 방식은 `delete`여야 한다** — 같은 카드의 이벤트가 같은 key로 여러 번 나간다(9-2 작성 뒤 9-3 수정). `compact`면 앞 이벤트가 지워진다.
- **파티션 수는 처음에 정하고 바꾸지 않는다** — 파티션은 `hash(key) % 파티션 수`로 정해진다. 늘리면 같은 카드가 다른 파티션으로 가 그 순간 순서가 깨진다(줄이는 것은 Kafka가 허락하지 않는다). 실제 이벤트가 흐르기 전에 정한다(「정하지 않은 것」 1).

### 지금 설정 때문에 지켜야 할 것

- **완료 이벤트는 running-service 한 대만 처리한다** — 파티션이 1개라 ALB 뒤 인스턴스가 여럿이어도 한 대만 읽고 나머지는 대기한다. 처리 결과는 DB에만 쓴다(architecture.md 「다중 인스턴스」). 처리량이 모자라면 파티션 수를 다시 정한다.
- **배포·스케일링 때 같은 완료 이벤트가 다시 온다** — 리밸런스 중 커밋하지 못한 이벤트를 다른 인스턴스가 받는다. inbox가 거른다([outbox-inbox.md](outbox-inbox.md)).
- **받는 쪽이 7일 넘게 멈추면 이벤트를 잃을 수 있다** — 요청 이벤트는 아웃박스 행이 남지만 다시 보내는 절차는 아직 없다.
- **컨슈머를 만들 때 `auto.offset.reset`을 정한다** — 처음 붙는 그룹과, 7일 넘게 비어 커밋한 오프셋이 지워진 그룹이 이 값을 따른다. `earliest`면 보관 중인 이벤트를 처음부터 받는다(아래 검증 메시지 포함).

### 실제 토픽에 검증 메시지가 남아 있다

ACL·접속 검증 때 실제 요청·완료 토픽(**운영 `prod` 토픽 포함**)에 JSON이 아닌 문자열 메시지를 보냈다(`kafka-acl-check-*`, `test-backend-ec2-*`, `test-ai-response-*`). 실제 그룹 이름으로 읽어 그 그룹의 오프셋은 지나갔지만:

- **새 그룹, 오프셋을 처음으로 되돌린 그룹, 7일 넘게 비어 오프셋이 지워진 그룹은 이 메시지를 받는다**(`earliest`일 때) — 본문을 읽지 못하는 메시지다. 완료 구독을 처음 붙일 때 이 경우가 생기기 쉽다([outbox-inbox.md](outbox-inbox.md) 「정하지 않은 것」 3).
- **실제 연동 전에 지운다**(`kafka-delete-records`). 기다려서 없애려면 약 14일이 걸린다 — 메시지가 적으면 세그먼트가 7일 뒤에 닫히고, 닫힌 뒤 다시 7일이 지나야 지워진다.
- 11-17 확인: `test.record-card.music.requested`에는 CDC 검증으로 나간 실제 카드 메시지 2개만 있고 예전 검증 메시지는 남아 있지 않다. `prod.record-card.music.requested`·`.generated`는 비어 있다(0개). test 완료 토픽과 `dev` 토픽은 확인 전이다.
- **앞으로 접속 검증은 실제 토픽에 하지 않는다** — 검증용 토픽을 따로 두고 권한도 따로 준다.

## 접속 보안 — TLS + SASL + ACL

### 리스너

| 리스너 | 열린 곳 | 프로토콜 | 인증 | 쓰는 쪽 |
|---|---|---|---|---|
| `SASL_SSL` | 브로커 주소 :9092 | TLS 1.3 / 1.2 | SCRAM-SHA-512 | 모든 클라이언트, 브로커끼리 통신 |
| `CONTROLLER` | 127.0.0.1:9093 | SASL_PLAINTEXT | PLAIN | 같은 EC2 안 컨트롤러만 |

- 클라이언트에게 알려 주는 주소(`advertised.listeners`)는 `SASL_SSL` 하나다 — 클라이언트는 이 주소로만 붙는다.
- 컨트롤러 리스너는 루프백이라 TLS 없이 PLAIN을 쓴다. 노드를 늘려 컨트롤러가 EC2 밖과 통신하게 되면 TLS를 붙인다.

### TLS

자체 CA(10년)가 Kafka 서버 인증서(1년)에 서명했다. 클라이언트는 CA 인증서로 서버 인증서를 검증한다.

| 가진 쪽 | 파일 | 쓰임 |
|---|---|---|
| 브로커 | 키스토어 (PKCS12) | 서버 인증서 + 개인키 — 접속을 받을 때 낸다 |
| Kafka EC2 | 트러스트 스토어 (PKCS12) | Kafka EC2 안에서 클라이언트로 붙는 쪽(브로커끼리 통신, 관리자·계정별 CLI 설정)이 서버 인증서를 검증한다 |
| running-service · AI 서버 · Debezium | **CA 인증서 (PEM)** | 서버 인증서를 검증한다. 트러스트 스토어 비밀번호가 필요 없다 |

- **서버만 인증서를 낸다** — 클라이언트 인증서는 요구하지 않는다(`ssl.client.auth=none`). 클라이언트가 누구인지는 TLS 위의 SASL로 증명한다.
- **호스트 검증을 켠다** (`ssl.endpoint.identification.algorithm=https`) — 서버 인증서의 SAN에는 **브로커 주소 하나**만 들어 있고, 이 주소는 브로커가 리스너를 여는 주소·클라이언트에게 알려 주는 주소(`advertised.listeners`)와 같다. 클라이언트는 그 주소로 붙어야 하고, 다른 주소(DNS 이름·다른 IP)로 붙으면 검증에 실패한다. 끄지 않는다.
- **이 주소는 EC2의 VPC 안 주소다** — 리스너가 이 주소에 바인딩하므로 EC2 네트워크 인터페이스에 있는 주소여야 하고, Elastic IP(공인 주소)는 인터페이스에 없어 바인딩할 수 없다. 그래서 클라이언트는 **같은 VPC 안(또는 피어링)에서만** 붙는다. VPC 안 주소는 인스턴스를 멈췄다 켜도 바뀌지 않지만, 인스턴스를 새로 만들면 바뀐다 — 그때는 서버 인증서를 다시 발급하고 `KAFKA_BOOTSTRAP_SERVERS`도 바꾼다. Elastic IP로 붙으면 SAN과 달라 실패한다.

**주의**

- **서버 인증서는 1년마다 만료된다** — 만료되면 모든 클라이언트의 접속이 한꺼번에 실패한다. 만료 전에 같은 CA로 다시 발급해 키스토어만 바꾸면 클라이언트는 그대로다. 만료일과 갱신 알림은 「정하지 않은 것」 2.
- **CA 개인키가 있으면 누구나 신뢰받는 서버 인증서를 만든다** — 갱신할 때만 필요하다. 보관 위치는 「정하지 않은 것」 2.
- **CA 인증서는 클라이언트 서버마다 있어야 한다** — 지금은 테스트 백엔드 서버에 손으로 복사했다. running-service는 Auto Scaling·Docker로 배포되므로 새 인스턴스·컨테이너에서도 `KAFKA_SSL_CA_LOCATION` 경로에 파일이 있어야 한다. CA 인증서는 공개 정보라 시크릿이 아니다 — 배포 방법은 「정하지 않은 것」 3.

### SASL

- 메커니즘은 **SCRAM-SHA-512** 하나다. 계정은 KRaft 메타데이터에 저장된다 — 계정을 더하거나 비밀번호를 바꿔도 브로커를 재시작하지 않는다.
- 관리자 `kafka-admin`은 클러스터를 처음 만들 때(저장소 포맷) 등록했다. 서비스 계정은 그 뒤 관리자가 추가한다.
- 비밀번호가 든 설정 파일 — Kafka EC2 안(서버 설정, 관리자·계정별 클라이언트 설정)은 `root:kafka 640`, 다른 서버의 클라이언트 설정은 `600`으로 둔다.
- **`kafka-admin` 비밀번호는 두 군데에 따로 있다** — SCRAM(KRaft 메타데이터, 클라이언트·브로커끼리)과 컨트롤러 리스너의 PLAIN(서버 설정 파일). 한쪽만 바꾸면 나머지 쪽 통신이 끊긴다.

| 계정 | 있음 |
|---|---|
| `kafka-admin` | O — super user (토픽·계정·ACL 관리, 브로커끼리·컨트롤러 통신) |
| `dev.running-service` · `test.running-service` | O |
| `dev.runiverse-ai` · `test.runiverse-ai` | O |
| `test.debezium` | O (11-17) |
| `prod.debezium` | O (11-17) |
| `dev.debezium` | X — dev는 커넥터가 없어 만들지 않는다 |

### ACL

- `StandardAuthorizer`, **허용하지 않은 것은 모두 거부**한다(`allow.everyone.if.no.acl.found=false`).
- **서비스 계정에는 `CREATE`를 주지 않는다** — 토픽은 관리자만 만든다.
- **ACL은 이름 하나씩(LITERAL) 준다** — 접두어(PREFIXED)로 묶으면 나중에 같은 접두어로 생긴 토픽에 권한이 따라간다.
- **컨슈머 그룹은 따로 만들지 않는다** — 그룹은 컨슈머가 처음 붙을 때 생긴다. 계정에 그 그룹의 `READ`만 준다.

| 계정 | 리소스 | 지금 | 목표 (CDC 적용 뒤) |
|---|---|---|---|
| `{env}.running-service` | 토픽 `requested` | prod: **없음**(11-17에 뺐다) · test·dev: `WRITE` `DESCRIBE` 남아 있음 ⚠️ | 없음 |
| `{env}.running-service` | 토픽 `generated` | `READ` `DESCRIBE` | 그대로 |
| `{env}.running-service` | 그룹 `{env}.running-service.record-card-music` | `READ` | 그대로 |
| `{env}.runiverse-ai` | 토픽 `requested` | `READ` `DESCRIBE` | 그대로 |
| `{env}.runiverse-ai` | 토픽 `generated` | `WRITE` `DESCRIBE` | 그대로 |
| `{env}.runiverse-ai` | 그룹 `{env}.runiverse-ai.record-card-music` | `READ` | 그대로 |
| `{env}.debezium` | 토픽 `requested` | test·prod: `WRITE` `DESCRIBE` (11-17) | `WRITE` `DESCRIBE` |
| `{env}.debezium` | Connect 내부 토픽 3개 | test·prod: `READ` `WRITE` `DESCRIBE` `DESCRIBE_CONFIGS` (11-17) | 그대로 |
| `{env}.debezium` | 그룹 `{env}.debezium-connect` | test·prod: `READ` (11-17) | 그대로 |
| `{env}.debezium` | 토픽 `{env}.debezium-heartbeat.outbox` | test·prod: `WRITE` `DESCRIBE` (11-17) | 그대로 |

- ⚠️ **running-service가 지금 요청 토픽에 쓸 수 있다** — 직접 발행하던 설계로 만든 권한이다. 아웃박스 + CDC에서는 running-service가 Kafka에 쓰지 않으므로, Debezium 권한을 준 뒤 뺀다. **prod는 11-17에 뺐다**(`prod.debezium`에 먼저 주고 `prod.running-service`에서 뺐다). test·dev에는 아직 남아 있다. 남아 있는 동안은 실수로 넣은 직접 발행 코드가 권한 오류 없이 동작한다 — 리뷰에서 running-service에 Kafka 프로듀서가 들어오지 않는지 본다(outbox-inbox.md 「앱은 Kafka로 직접 보내지 않는다」).

## running-service 접속 설정

running-service에는 아직 Kafka 클라이언트가 없다 — 요청은 Debezium이 보낸다. 아래는 **완료 구독을 만들 때** 쓴다. 지금 쓰는 값은 `KAFKA_ENV`(아웃박스 `topic` 값)뿐이다.

| 변수 | 예 | 설명 |
|---|---|---|
| `KAFKA_ENV` | `dev` | `{env}` 값. 토픽·그룹 이름을 이 값 하나로 만든다. `dev`·`test`·`prod`가 아니면 기동하지 않는다. 배포 환경은 Parameter Store `/runiverse/test/KAFKA_ENV`(`test`)·`/runiverse/deploy/KAFKA_ENV`(`prod`)에 있다(11-17). **붙는 DB의 환경을 따른다** — 로컬 Postgres면 `dev`, 로컬에서 test DB에 붙으면 `test`([cdc.md](cdc.md) 「`KAFKA_ENV`는 기계가 아니라 DB를 따른다」) |
| `KAFKA_BOOTSTRAP_SERVERS` | `<브로커 VPC 주소>:9092` | 지금은 노드 하나. 인증서 SAN과 같은 주소를 쓴다 |
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` | 배포 환경은 항상 `SASL_SSL`. 로컬 Docker Kafka를 만들면(아직 없다) 그것만 `PLAINTEXT` |
| `KAFKA_SASL_MECHANISM` | `SCRAM-SHA-512` | |
| `KAFKA_SASL_USERNAME` | `dev.running-service` | `{env}.running-service` |
| `KAFKA_SASL_PASSWORD` | | 시크릿 — 로그·출력에 남기지 않는다 |
| `KAFKA_SSL_CA_LOCATION` | `/app/config/kafka-ca.crt` | CA 인증서(PEM) 경로 — `ssl.truststore.type=PEM`으로 읽는다. 배포에서는 절대 경로 |

## Debezium을 붙일 때 Kafka에 더할 것

자세한 설계는 [cdc.md](cdc.md)에 둔다. Kafka 쪽에서 준비할 것만 적는다.

- [x] 계정 `{env}.debezium` (SCRAM-SHA-512) — test·prod
- [x] 요청 토픽 `WRITE` `DESCRIBE`를 `{env}.debezium`에 주고 `{env}.running-service`에서 뺀다 — test는 주기만 했다(running-service 권한 남음, 위 ⚠️), prod는 둘 다 했다
- [x] Kafka Connect 내부 토픽 3개(설정·오프셋·상태) — test·prod — 관리자가 미리 만든다(자동 생성이 꺼져 있다). **`cleanup.policy=compact`**, 복제 계수 1, 설정 토픽은 파티션 1개. **환경마다 따로, 이름은 `{env}.`로 시작**
- [x] Connect 워커 그룹(환경마다 따로)과 내부 토픽 권한 — test·prod
- [x] Debezium heartbeat 토픽 — test·prod. 미리 만들고 `{env}.debezium`에 `WRITE` ([cdc.md](cdc.md) 「Debezium 설정에서 정할 것」)
- [x] Connect가 도는 곳에 CA 인증서 — test는 Connect를 Kafka EC2에 같이 두어 이미 있다
- [ ] 중복 방지(`enable.idempotence=true`)를 켠 채로 발행되는지 확인 — 지금까지 송수신 검증은 끄고 했다. Connect 워커 설정에 `producer.enable.idempotence=true`로 명시해 기본값에 기대지 않는다

## 보내는 쪽은 중복 방지를 켠다

- **Kafka에 쓰는 모든 프로듀서는 `enable.idempotence=true`로 보낸다** — 재전송 중복을 줄이고 재전송 때도 파티션 안 순서를 지킨다. 4.1.2에서는 토픽 `WRITE`만 있으면 되어 권한을 더 줄 것이 없다.
- 브로커가 강제하는 설정이 아니다 — 클라이언트마다 켜야 한다. Java 클라이언트(Debezium·Spring Kafka)는 기본이 켜짐이지만, **AI 서버의 Python 클라이언트는 라이브러리에 따라 기본이 꺼짐**(예: `confluent-kafka`)이라 명시해야 한다.
- 켜도 받는 쪽 중복은 남는다 — inbox는 그대로 둔다([outbox-inbox.md](outbox-inbox.md)).
- 접속 검증 명령에도 끄는 옵션을 붙이지 않는다.

## 정하지 않은 것

1. **파티션 수** — 지금 1이다. 그대로 갈지, 실제 이벤트가 흐르기 전에 늘릴지.
2. **인증서 관리** — 클러스터마다 서버 인증서 만료일, 만료 전 알림, CA 개인키 보관 위치(브로커 EC2 밖), 갱신 절차.
3. **CA 인증서 배포** — running-service(Auto Scaling·Docker)·AI 서버·Connect에 어떻게 넣을지(SSM·이미지·사용자 데이터).
4. **모니터링** — 브로커 상태·디스크·컨슈머 지연을 어떻게 볼지. 지금은 없다([metrics-convention.md](../metrics-convention.md)와 맞춘다).
5. **검증 메시지 정리와 검증용 토픽** — 위 「실제 토픽에 검증 메시지가 남아 있다」.
