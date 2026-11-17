# CDC — 아웃박스 행을 Kafka로

커밋된 `outbox_events` 행을 Debezium이 Postgres WAL에서 읽어 Kafka에 발행하는 경로의 **지금 상태, 설계, 준비할 것**을 적는다. 설치 명령은 적지 않는다.

| 무엇 | 어디 |
|---|---|
| 아웃박스 테이블 (정본) | [erd.md](../erd.md) `outbox_events` |
| 아웃박스를 쓰는 코드 규칙 | [outbox-inbox.md](outbox-inbox.md) |
| 발행되는 이벤트 계약 (헤더·key·본문) | [api-spec.md](../api-spec.md) 9-5 |
| Kafka 클러스터·계정·권한 | [kafka.md](kafka.md) |

## 지금 상태

| 항목 | 상태 |
|---|---|
| 아웃박스 행 쓰기 | **됨** — 9-2 작성 확정 트랜잭션에서 쓴다 |
| 요청 토픽 | 있음 (`test`·`dev` 공유 클러스터, `prod` 운영 클러스터) |
| Kafka 계정 `{env}.debezium` | 없음 |
| 커넥터 DB 계정 | test DB `debezium` **있음** — `LOGIN REPLICATION`, `outbox_events` SELECT만 (11-17). 밖에서 접속(`pg_hba.conf`)은 아직 막혀 있다. prod RDS `debezium` **있음** — `rds_replication` 역할, `outbox_events` SELECT, `cdc_heartbeats` SELECT·INSERT·DELETE (11-17) |
| publication | test DB `outbox_publication` **있음** — `outbox_events` + `cdc_heartbeats`, `publish = 'insert'` (11-17). prod도 같은 모양으로 **있음** (11-17). prod 테이블 두 개는 `admin`이 손으로 만들었고(토픽 CHECK는 `prod`만), 앱 계정(`runiverse_app`)은 기본 권한으로 쓰기 권한을 받았다 |
| Postgres 논리 복제 | test DB(PostgreSQL 17.10, **Kafka와 같은 EC2**, 앱과는 다른 EC2) **켜짐** — `wal_level=logical`, `max_slot_wal_keep_size=5GB`, 슬롯 없음 (11-17). prod RDS(**PostgreSQL 18.3**) **켜짐** — 사용자 정의 파라미터 그룹 `runiverse-backend-deploy-postgres18`(`rds.logical_replication=1`, `max_slot_wal_keep_size=10240` MB)으로 바꾸고 재부팅, `wal_level=logical`, 슬롯 없음 (11-17) |
| Kafka Connect · Debezium | test: **Debezium 3.7.1.Final** 플러그인을 `/opt/kafka/plugins`에 넣었다(Kafka 4.1.2의 Connect를 systemd로 띄울 예정, Kafka·DB EC2에 같이). 아웃박스 변환기는 `debezium-connect-plugins` jar에 들어 있다. Connect는 systemd `kafka-connect`로 떠 있다(REST는 `127.0.0.1:8083`만, 그룹 `test.debezium-connect`). **커넥터 `test-outbox` RUNNING** — 설정은 `infra/kafka/connectors/test-outbox.json`, 슬롯 `debezium_outbox_test`, 스냅샷 없음(`no_data`), heartbeat 1분 + `cdc_heartbeats`에 쓰는 `heartbeat.action.query` (11-17). **실제 카드로 발행 검증 완료**(아래 「검증」). prod: 같은 3.7.1.Final 플러그인을 prod Kafka EC2 `/opt/kafka/plugins`에 넣고 Connect를 systemd `kafka-connect`로 띄웠다(REST는 `127.0.0.1:8083`만, 그룹 `prod.debezium-connect`). **커넥터 `prod-outbox` RUNNING** — 설정은 `infra/kafka/connectors/prod-outbox.json`(DB 주소·비밀번호는 서버 파일 참조), 슬롯 `debezium_outbox_prod`, 스냅샷 없음, heartbeat 쿼리 동작 확인 — 붙인 지 약 12분 뒤 슬롯 지연 712 bytes (11-17). 실제 이벤트 검증은 아웃박스 코드 배포 뒤 |

- **test·prod 모두 11-17부터 CDC가 붙어 있다.** prod는 아웃박스 코드가 아직 배포 전이라 나가는 이벤트가 없다 — 커넥터를 먼저 붙였으므로 배포 뒤 첫 카드부터 빠짐없이 나간다. 아웃박스 코드가 CDC보다 먼저 배포되면 그동안 만든 카드의 음악은 `GENERATING`에 머문다 — 커넥터를 처음 붙일 때 그 행을 보낼지 정한다(「정하지 않은 것」 2).

## 구조

```text
running-service ──INSERT──▶ Postgres    
                             outbox_events ─ WAL ─▶ 복제 슬롯 ─▶ Kafka Connect
                             (publication: outbox_events만)        └ Debezium Postgres 커넥터
                                                                     └ Outbox Event Router
                                                                            │ SASL_SSL ({env}.debezium)
                                                                            ▼
                                                              {env}.record-card.music.requested
```

- **환경마다 경로가 하나씩 따로다** — DB 하나에 커넥터 하나, 슬롯 하나. 커넥터 하나가 여러 환경의 DB를 읽지 않는다.
- **전달은 최소 한 번이다** — Connect는 어디까지 보냈는지를 주기적으로 기록하고, 그 뒤에야 슬롯 위치를 앞으로 민다. 재시작하면 마지막 기록 뒤의 행을 다시 보낸다. 받는 쪽 inbox가 거른다([outbox-inbox.md](outbox-inbox.md)).

### 환경

DB는 세 환경이 모두 다르다.

| 환경 | DB | Kafka | 커넥터 |
|---|---|---|---|
| `dev` | 로컬 Postgres | 공유 클러스터 (`dev.*`) | **없다** — VPC 안의 Connect가 로컬 Postgres에 닿지 않는다 |
| `test` | test DB — **EC2에 직접 설치한 Postgres, Kafka와 같은 EC2** | 공유 클러스터 (`test.*`) | test DB를 읽는 커넥터 하나 |
| `prod` | prod RDS | 운영 클러스터 | prod RDS를 읽는 커넥터 하나 |

- **test와 prod는 DB 종류가 달라 준비 방법이 다르다** — test는 설정 파일과 재시작, prod는 RDS 파라미터 그룹과 재부팅(아래 「논리 복제 켜기」). test에서 검증한 절차를 prod에 그대로 쓰지 못하는 부분이 있다.

- **로컬에서 CDC까지 확인할 때는 로컬 앱을 test DB에 붙인다**(bastion 경유) — 로컬 앱이 test DB에 쓴 아웃박스 행을 test 커넥터가 읽어 발행한다. 로컬 Postgres에 쓴 행은 아무도 보내지 않는다.

### `KAFKA_ENV`는 기계가 아니라 DB를 따른다

**test DB에 붙은 앱은 로컬이라도 `KAFKA_ENV=test`로 띄운다.**

- 아웃박스 `topic` 값은 쓰는 앱의 `KAFKA_ENV`로 정해지고, 커넥터는 그 값대로 보낸다. 로컬 앱을 `dev`로 띄운 채 test DB에 붙으면 test 커넥터가 `dev.*` 토픽으로 보내려 한다.
- `test.debezium`에는 `dev.*` 쓰기 권한이 없어 **커넥터가 멈춘다. 재시작해도 같은 행에서 다시 멈춘다** — 행은 이미 WAL에 있어 지워도 소용없다. 그동안 슬롯이 WAL을 붙잡는다(아래 「복제 슬롯」).
- **막는 장치(제안)**: test DB의 `ck_outbox_event_topic`을 `test` 토픽만 허용하도록 좁힌다 — 운영 DDL이 `prod`만 허용하는 것과 같다. 그러면 잘못 띄운 앱은 9-2 저장에서 바로 실패해 커넥터까지 가지 않는다.

## Postgres 쪽

### 논리 복제 켜기

| 환경 | 켜는 곳 | 적용 |
|---|---|---|
| `test` (EC2 Postgres) | `postgresql.conf`의 `wal_level=logical` | **Postgres 재시작** — 설정 다시 읽기(reload)로는 바뀌지 않는다 |
| `prod` (RDS) | 사용자 정의 파라미터 그룹의 `rds.logical_replication=1` | **인스턴스 재부팅** — 점검 시간을 잡는다 |
| `dev` (로컬) | 필요 없다 — 커넥터가 없다 | |

- **prod**: 기본 파라미터 그룹은 고칠 수 없어, 기본 그룹을 쓰고 있으면 사용자 정의 그룹을 만들어 바꿔 끼운다(이것도 재부팅). 다른 DB와 같이 쓰는 그룹이면 고치지 말고 따로 만든다.
- **test**: Connect가 도는 곳에서 5432로 붙을 수 있어야 한다 — `listen_addresses`, `pg_hba.conf`에 커넥터 계정과 Connect 주소, 보안 그룹. 논리 복제 접속은 `pg_hba.conf`에서 `replication` 키워드가 아니라 **DB 이름**으로 맞는 줄을 찾는다.
- `max_replication_slots` · `max_wal_senders`는 기본값(10)으로 충분하다 — 커넥터가 환경마다 하나다.

### 커넥터 전용 DB 계정

- **슈퍼유저를 주지 않는다** — 복제 권한과 `outbox_events` 조회 권한만 준다. 복제 권한은 test(직접 설치)는 계정의 `REPLICATION` 속성, prod(RDS)는 `rds_replication` 역할이다. 앱 계정(`DATABASE_USERNAME`)을 같이 쓰지 않는다.
- 비밀번호는 SSM에 둔다.

### publication — `outbox_events`만

- **publication은 관리자가 `outbox_events` 하나만 담아 미리 만든다.** 커넥터가 스스로 만들지 않게 한다(`publication.autocreate.mode=disabled`) — 커넥터 계정에 테이블 소유권을 주지 않아도 되고, 다른 테이블(사용자 정보·위치)의 변경이 커넥터로 나가지 않는다.
- **INSERT만 내보낸다**(`publish = 'insert'`) — 아웃박스는 한 번 쓰고 고치지 않아 의미 있는 변경은 INSERT뿐이다. 지난 행을 지워도 DELETE가 Debezium에 가지 않는다.
- 이름은 `outbox_publication` — 커넥터 `publication.name`에 쓴다.
- 새 이벤트를 더해도 테이블은 같아 publication을 바꿀 일이 없다. heartbeat 방식에 따라 publication을 고쳐야 할 수 있다 — UPDATE로 미는 테이블을 담으면 `publish`에 `update`가 필요하다(「정하지 않은 것」 3).

### 복제 슬롯 — 가장 조심할 것

- **슬롯은 커넥터가 확인한 위치 뒤의 WAL을 지우지 못하게 붙잡는다.** 커넥터가 멈추거나 Kafka가 내려가면 그동안 WAL이 쌓여 **DB 디스크가 차고, 차면 DB 전체가 멈춘다** — 음악 기능이 아니라 서비스 전체 장애다.
- **커넥터가 정상이어도 WAL이 쌓일 수 있다** — 슬롯 위치는 publication에 든 테이블의 변경이 있어야 앞으로 간다. 기록 카드를 아무도 만들지 않는 동안 다른 테이블(러닝 기록 등)의 쓰기로(RDS는 자체 쓰기도 한다) WAL은 계속 늘지만 슬롯은 제자리다. **heartbeat 쿼리로 슬롯을 민다** — Debezium은 publication 안 변경을 처리할 때만 슬롯 위치를 민다. 그래서 주기(`heartbeat.interval.ms`)만 켜면 heartbeat 기록이 Kafka로 나가도 슬롯은 제자리다(test에서 확인, 아래 「검증」). publication에 `cdc_heartbeats`(erd)를 넣고, Debezium이 heartbeat마다 그 테이블에 한 줄 쓰게 한다(`heartbeat.action.query` — INSERT와 하루 지난 행 DELETE를 한 문장으로). 그 INSERT가 Debezium에게 돌아오면서 슬롯이 앞으로 간다. 이 테이블은 `table.include.list`에 없어 Kafka로는 나가지 않는다.
- **막는 장치는 두 겹이다**
  - 상한: `max_slot_wal_keep_size` — 넘으면 Postgres가 슬롯을 버리고 WAL을 지운다. 디스크는 지키지만 그 슬롯은 못 쓰게 되어 커넥터를 다시 만들어야 한다. 버려진 구간의 이벤트는 아웃박스 행으로 남아 있다(다시 보내는 절차는 아직 없다).
  - 알람: 슬롯 지연(WAL 보유량)과 디스크 여유를 본다. 상한에 닿기 전에 알아야 한다. **test(EC2)는 RDS처럼 CloudWatch 지표가 없어** 슬롯 상태를 직접 조회해 내보내야 한다 — `pg_replication_slots`의 `wal_status`(상한에 가까워지면 바뀐다)·`safe_wal_size`(상한까지 남은 양)·`invalidation_reason`(상한을 넘어 버려진 이유).
- **커넥터를 영구히 내리면 슬롯을 지운다** — 남은 슬롯은 아무도 읽지 않는데 WAL을 계속 붙잡는다. 환경을 정리할 때 체크리스트에 넣는다.
- 슬롯 이름은 환경마다 고정한다(`slot.name`) — 커넥터를 다시 만들어도 같은 슬롯을 이어 쓴다.

## Debezium 설정에서 정할 것

이벤트 계약(9-5)이 이 설정으로 결정된다 — 바꾸면 받는 쪽이 깨진다.

| 아웃박스 컬럼 | Kafka 메시지 | 설정 |
|---|---|---|
| `outbox_event_id` | 헤더 `id` | `table.field.event.id=outbox_event_id` |
| `aggregate_id` | key | `table.field.event.key=aggregate_id` |
| `topic` | 토픽 | `route.by.field=topic`, `route.topic.replacement=${routedByValue}` — 기본값은 앞에 `outbox.event.`을 붙이므로 꼭 바꾼다 |
| `payload` | value | `table.field.event.payload=payload` |

- **key·value는 문자열 그대로 내보낸다** (제안: `key.converter`·`value.converter`를 `StringConverter`로)
  - key — JSON 변환기를 쓰면 `"..."`처럼 따옴표가 붙은 값이 나가 AI 서버가 보내는 key(따옴표 없는 카드 ID)와 달라진다.
  - value — `jsonb`는 Debezium에서 문자열로 읽힌다. JSON 변환기를 쓰면 문자열로 한 번 더 감싸져(`"{\"user_id\":...}"`) 계약과 달라진다. 펼치는 설정(`table.expand.json.payload`)은 **`null` 필드를 기본으로 빼 버려** "값이 없어도 키를 `null`로 둔다"는 규칙과 맞지 않는다. 문자열 변환기는 DB에 저장된 JSON 텍스트를 그대로 보낸다.
  - `jsonb`는 저장할 때 키 순서를 바꾼다 — 받는 쪽은 키 순서에 기대지 않는다.
- **커넥터는 `outbox_events`만 읽는다** (`table.include.list`), **라우터는 아웃박스 메시지에만 적용한다** — heartbeat 메시지가 라우터를 지나지 않게 한다.
- **`tombstones.on.delete=false`** — 지난 행을 지우게 되면 삭제가 Kafka로 나가지 않아야 한다(정리 방식을 정할 때 함께 검증).
- **heartbeat는 Kafka 토픽에도 메시지를 쓴다** — 토픽 자동 생성이 꺼져 있어 관리자가 미리 만들고 `{env}.debezium`에 쓰기 권한을 준다. 이름은 `{env}.debezium-heartbeat.outbox`(`topic.heartbeat.prefix={env}.debezium-heartbeat` + `topic.prefix=outbox`)([kafka.md](kafka.md)).
- **스냅샷** — 처음 붙을 때 이미 있는 행을 읽을지(`snapshot.mode`). 「정하지 않은 것」 2.
- 프로듀서 중복 방지를 명시한다([kafka.md](kafka.md) 「보내는 쪽은 중복 방지를 켠다」).

## Kafka Connect

### 어디서 돌리든 필요한 것

- DB 5432와 Kafka 9092에 닿는 네트워크(보안 그룹). Kafka에는 **인증서 SAN과 같은 VPC 안 주소**로 붙는다.
- 그 환경 클러스터의 CA 인증서 — test와 prod의 CA가 다르다.
- 시크릿 두 개: DB 계정 비밀번호, `{env}.debezium` 비밀번호.
- 환경마다 따로인 Connect 그룹·내부 토픽 3개 — 이름은 `{env}.`로 시작한다(test·dev가 한 클러스터를 같이 쓴다).

### 주의

- **REST API(기본 8083)는 인증이 없다** — 이 API로 커넥터를 만들고 설정을 읽을 수 있다. 외부에 열지 않는다(루프백 또는 보안 그룹으로 막는다).
- **커넥터 설정은 Connect 설정 토픽에 평문으로 저장된다** — 비밀번호를 커넥터 설정에 직접 넣지 않고, 워커의 config provider(파일·환경 변수)로 실행할 때 읽게 한다. REST API로 설정을 조회해도 비밀번호가 보이지 않는다.
- 커넥터 설정 JSON은 `infra/kafka/`에 둔다 — 비밀번호 자리는 provider 참조만 남는다.

## 검증 — 테스트가 못 보는 것

E2E에는 Kafka·Debezium이 없다. 처음 붙일 때(test 환경) 직접 확인한다.

- [x] 카드를 하나 만들면 요청 토픽에 메시지가 **하나** 나간다 — test 11-17
- [x] 헤더 `id`가 `outbox_event_id`와 같다 — test 11-17. 헤더에는 `id` 말고도 Debezium이 붙이는 `__debezium.context.*`(커넥터 이름·태스크·실행 ID)가 함께 온다. 받는 쪽은 `id`를 이름으로 찾는다
- [x] key가 따옴표 없는 카드 ID다 — test 11-17
- [x] value가 `payload` JSON 그대로다 — 문자열로 감싸지지 않고, `null` 필드가 남아 있다 — test 11-17 (키 순서는 `jsonb`가 바꾼다)
- [x] 토픽이 `outbox_events.topic` 값 그대로다(`outbox.event.` 접두어 없음) — test 11-17
- [x] 커넥터를 재시작해도 이벤트가 빠지지 않는다(중복은 허용) — test 11-17: Connect를 멈춘 채 카드를 만들고(9-2 성공, 슬롯 지연 85 kB) 다시 켜자 그 이벤트가 아웃박스 ID 그대로 나갔다. 이번에는 중복 없음
- [x] 카드를 만들지 않고 두어도 슬롯 지연이 늘지 않는다(heartbeat) — test 11-17. **주기(`heartbeat.interval.ms`)만으로는 부족했다**: publication 밖 테이블에 20만 행을 써 WAL을 18 MB 늘리자, heartbeat 기록은 Kafka로 계속 나가는데도 지연이 19 MB에서 줄지 않았다. Debezium 로그가 이유를 말한다 — `LSN flush mode 'connector': Debezium will flush LSN on event processing ... consider using heartbeat.action.query`(publication 안 변경을 처리할 때만 슬롯을 민다). `cdc_heartbeats` + `heartbeat.action.query`를 더하자 그 19 MB가 약 3분 만에 792 bytes로 풀렸다. 슬롯은 heartbeat 직후가 아니라 Connect가 오프셋을 기록한 뒤(기본 60초 간격) 밀린다
- [ ] AI 서버 계정으로 읽힌다 — AI 쪽 확인 대기(우리가 AI 그룹으로 읽으면 AI가 받을 메시지를 가져가므로 AI 서버가 확인한다)

### prod — 배포 전 확인 (11-17)

아웃박스 코드를 배포하기 전에, 검증용 아웃박스 행을 손으로 넣어 prod 경로를 한 번 돌렸다. prod에만 있는 차이(RDS 암호화 접속 `sslmode=require`, 파일에서 읽는 DB 주소, PostgreSQL 18, prod 계정·권한)가 이 경로에 있어서다.

- [x] 행 하나(`aggregate_id = 'prod-cdc-smoke-001'`, 본문 `{"check": "prod-cdc-smoke", "memo": null}`)를 넣자 `prod.record-card.music.requested`에 메시지가 **하나** 나갔다 — 헤더 `id` = `outbox_event_id`, key는 따옴표 없는 `prod-cdc-smoke-001`, value는 JSON 그대로이고 `null`이 남았다. 헤더 모양은 test와 같다
- [x] 확인 뒤 지웠다 — Kafka는 `kafka-delete-records`로 그 메시지 앞까지 지웠고(토픽이 다시 0개), DB 행은 `DELETE`로 지웠다. **DB `DELETE`는 Kafka로 나가지 않았다**(publication이 INSERT만 내보낸다)
- 검증 메시지는 offset 2였다 — 0·1은 예전 ACL 검증 메시지가 보관 기간으로 지워진 자리다. 이 토픽의 시작 위치는 이제 3이다
- [ ] 배포 뒤 첫 실제 카드로 같은 항목을 한 번 더 본다
- [ ] AI prod 서버가 `prod.*` 계정·토픽·그룹으로 붙어 받는다 — AI 쪽

## 준비 순서

1. **DB** — 논리 복제 켜기(test: `wal_level` + 재시작, prod: 파라미터 그룹 + 재부팅) → `max_slot_wal_keep_size` → 커넥터 DB 계정 → publication(+ heartbeat 테이블)
2. **Kafka** — [kafka.md](kafka.md) 「Debezium을 붙일 때 Kafka에 더할 것」
3. **Connect** — 띄우고, CA 인증서·시크릿을 넣고, REST API를 막는다
4. **커넥터 등록** — `infra/kafka/`의 설정으로
5. **검증** — 위 체크리스트
6. **알람** — 슬롯 지연(prod: CloudWatch `prod-cdc-replication-slot-lag`, 11-17). test는 아직
7. test에서 끝난 뒤 prod에 같은 순서로 (prod는 재부팅 점검 시간)

## 정하지 않은 것

1. **Connect를 어디서 돌릴지** — Kafka EC2에 같이, 별도 EC2, 컨테이너. **test는 Kafka·DB EC2에 같이 둔다(11-17)** — 2GB(`t3.small`, 여유 약 280MiB)에서는 올릴 수 없어 `t3.medium`(4GB, 여유 약 2.2GiB)으로 키웠다. 같은 EC2라 DB를 밖에 열지 않는다(`localhost`). 실행 방식(systemd·Docker)은 아직. **prod도 Kafka EC2에 같이 둔다(11-17)** — prod Kafka EC2는 `t3.medium`(여유 약 2.5GiB, 디스크 73G 남음)이라 키우지 않아도 된다. RDS는 다른 곳이라 Connect가 네트워크로 붙는다 — RDS 보안 그룹에 Kafka EC2의 보안 그룹을 5432로 허용한다.
2. **처음 붙일 때 이미 쌓인 행** — 보낼지(스냅샷) 말지(`snapshot.mode=no_data`). 보내면 오래된 카드의 음악이 뒤늦게 만들어지고, 안 보내면 그 카드들은 `GENERATING`에 남는다. 스냅샷 행을 라우터가 어떻게 다루는지도 test에서 확인한다.
3. ~~heartbeat 테이블이 필요한지~~ — **정했다(11-17): 필요하다.** `cdc_heartbeats`(erd)를 publication에 더하고, Debezium이 1분마다 INSERT + 하루 지난 행 DELETE를 한 문장으로 쓴다. publication은 `publish = 'insert'` 그대로.
4. **슬롯 상한과 알람 값** — test는 `max_slot_wal_keep_size=5GB`로 정했다(루트 디스크 29G를 OS·**Kafka 로그**와 같이 쓰고 23G 남음, 평소 WAL 49M — WAL이 차면 Kafka도 같은 디스크라 함께 멈춘다). **prod는 정했다(11-17)**: `max_slot_wal_keep_size=10240` MB(100 GiB 중 10%, 스토리지 자동 조정 전에 멈춘다), CloudWatch 경보 `prod-cdc-replication-slot-lag` — `AWS/RDS` `OldestReplicationSlotLag`(prod DB 하나) 최대값이 5분 단위로 15분 연속 1GB 초과면 SNS `runiverse-prod-alerts`(이메일)로 알린다. 지표가 끊기면(슬롯이 사라지면) 위반으로 본다. **test는 알람이 아직 없다** — CloudWatch가 없어 `pg_replication_slots`를 직접 조회해 내보내야 한다.
5. **test DB의 토픽 CHECK를 `test`로 좁힐지** — 위 「`KAFKA_ENV`는 기계가 아니라 DB를 따른다」. test DB는 JPA 자동 생성(`update`)이라 엔티티 CHECK(세 환경 허용)로 만들어졌다. `update`는 이미 있는 CHECK를 고치지 않으므로, 같은 이름(`ck_outbox_event_topic`)으로 한 번 손으로 바꿔 두면 유지된다. 이벤트를 늘릴 때 test DB CHECK를 손으로 넓혀야 하는 것은 좁히든 안 좁히든 같다(자동 생성이 CHECK를 갱신하지 않는다).
6. **prod RDS 확인** — 버전은 **18.3으로 test(17.10)와 다르다**(11-17). Debezium 3.7은 둘 다 지원하지만, test에서 본 동작을 prod 검증 체크리스트로 다시 확인한다. RDS for PostgreSQL(Aurora 아님), `db.t3.small`, **단일 AZ** — 장애 조치로 슬롯을 잃을 걱정은 지금은 없다(Multi-AZ로 바꾸면 다시 본다). 파라미터 그룹은 **기본 그룹(`default.postgres18`)이라 고칠 수 없어** 사용자 정의 그룹을 만들어 바꿔 끼운다(재부팅). **스토리지 자동 조정이 켜져 있다(100 GiB → 최대 1000 GiB)** — 슬롯이 WAL을 붙잡으면 디스크가 차서 멈추는 대신 스토리지가 조용히 늘어나고 줄어들지 않는다(비용). `max_slot_wal_keep_size`와 슬롯 지연 알람이 이것도 막는다 (11-17).
7. **지난 행 정리** — 언제 지울지, 지울 때 Kafka로 아무것도 나가지 않는지.
8. **로컬에서 완료 이벤트 받기** — 완료 구독을 만들 때. 로컬 앱은 Kafka에 직접 닿지 않는다(브로커가 VPC 안 주소를 알려 주고 인증서도 그 주소라, SSH 터널로 우회되지 않는다). 로컬 Docker Kafka를 쓸지, test 서버에서 확인할지 정한다. 공유 클러스터의 `dev.*` 토픽·계정을 쓰는 곳도 이때 정해진다.
9. **로컬 dev에서 음악 요청을 보내지 않기 (나중에)** — 메일·푸시처럼 로컬에서는 실제로 보내지 않게 한다. 메일·푸시는 `local` 프로필이면 로그만 남기는 어댑터로 바뀐다(`LoggingEmailAdapter`·`LoggingPushAdapter`). 다만 아웃박스는 **로컬에서 test DB에 붙여 CDC를 확인할 때는 실제로 써야 해서** 같은 `local` 프로필로 끄면 그 확인을 못 한다 — 끄는 기준을 프로필이 아니라 붙는 DB(`KAFKA_ENV`)에 둘지 함께 정한다.
