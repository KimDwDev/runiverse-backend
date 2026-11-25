# 러닝 세션 분리 검증 실행 순서

러닝 세션 분리 검증을 실제로 돌리는 순서다. 운영에서 읽기만 하는 0단계 운영 증거를 먼저 뽑고, 그다음 측정 환경을 만들어 한 회차씩 돌린다. 무엇을 왜 재는지는 [verification.md](../../docs/msa/running-session-server/verification.md), 회차 규칙과 기준 숫자는 [PREREG.md](PREREG.md), 하네스 구성은 [README.md](README.md)에 있다.

> 상태: 「0. 운영 증거」는 11-25에 ASG · Kibana · Grafana 몫을 뽑았다(RESULTS 「0. 운영 증거」). 0-3 대조일과 남은 배포 창(10-06 18:35 뒤 · 10-23 · 11-20), 0-4 운영 DB 집계, 0-5의 Prometheus REST 비율 · CloudWatch 크레딧 · HikariCP 기준선(패널을 더한 뒤)이 남았다 — 운영 읽기 권한이 있는 개발자가 한다. 「1. 준비」부터는 계획 — 하네스 v2가 생기고 PREREG를 얼린 뒤에 돈다.

| 무엇 | 어디 |
|---|---|
| 0단계가 판정하는 것 | [verification.md](../../docs/msa/running-session-server/verification.md) 「0단계 — 운영 증거」 |
| 결과를 적는 칸 | [RESULTS.md](RESULTS.md) 「0. 운영 증거」 「회차 색인」 |
| 읽어 둘 운영 설정 값 | [platform.md](../../docs/msa/platform.md) 「확인할 운영 값」 |
| 1차 캠페인의 실행 순서 (접속 · 시드 · 모니터링 EC2) | [../RUNBOOK.md](../RUNBOOK.md) |

> 주소·키·비밀값·인스턴스 ID는 적지 않는다 — 명령의 `<…>`는 실행할 때 채운다. 운영 증거는 합계만 남긴다.

## 0. 운영 증거

운영 배포 때 진행 중이던 러닝이 몇이었고, 연결이 몇 개 끊겼고, 몇 번 다시 붙었는지를 센다 — G1이 실제로 얼마나 큰지다. ASG 활동 기록은 약 6주만 남는다 — 새 배포를 더할 때는 6주 안에 뽑는다.

- **운영에서는 읽기만 한다** — 콘솔 · CLI · Kibana · 읽기 전용 SQL.
- **결과는 합계만 옮긴다** — 사용자 ID · 좌표 · 방 번호 · 인스턴스 ID는 RESULTS에 옮기지 않는다.
- **시각은 모두 KST로 적는다** — DB의 `*_at`은 시간대 없는 KST다([erd.md](../../docs/erd.md) 「0. 공통 규칙」). ASG 기록은 UTC라 9시간을 더한다.

### 0-1. 배포 목록

운영 배포 = 조직 저장소(`upstream`) `main`의 first-parent 병합이다(병합하면 `backend-cd-prod.yml`이 instance refresh를 건다).

```bash
cd "$(git rev-parse --show-toplevel)"
git fetch upstream
TZ=Asia/Seoul git log upstream/main --first-parent --merges \
  --date=format-local:'%Y-%m-%d %H:%M' --format='%h %ad %s'
load_test/msa/scripts/deploy-cadence.sh          # 배포마다 실시간 · 끝내기 · 나머지 파일 수
```

`deploy-cadence.sh`의 `live` · `finish/record` 줄을 보고 그 배포에 세션 배포도 필요했는지(기능 변경인지)를 표시한다 — 주석 · 이름 바꾸기는 세지 않는다.

### 0-2. 옛 인스턴스가 끝난 시각

instance refresh는 새 인스턴스를 띄우고 워밍업(`InstanceWarmup` 600초)을 기다린 뒤 옛 인스턴스를 끝낸다. 연결이 끊기는 시각은 병합 시각이 아니라 이 종료 시각이다.

```bash
aws autoscaling describe-scaling-activities \
  --auto-scaling-group-name "<운영 ASG 이름 — GitHub 변수 PROD_ASG_NAME>" \
  --max-items 200 \
  --query "Activities[?starts_with(Description, 'Terminating')].[StartTime, EndTime, StatusCode]" \
  --output table
```

- 병합 시각 뒤 처음 나오는 `Terminating`의 `StartTime` · `EndTime`(UTC) + 9시간이 그 배포의 내리기 시작 · 종료 시각이다. 둘 다 적고, 0-3의 창은 종료(`EndTime`)로 잡는다.
- **기록이 사라진 배포**는 '병합 + 10분'을 쓰고 RESULTS에 `추정`이라 적는다 — 빌드 시간만큼 실제는 더 늦다.
- **병합과 짝이 없는 `Terminating`**(시작 템플릿 교체 같은 재시작)도 한 줄로 적고 `코드 병합 아님`이라 쓴다 — 0-1의 목록에는 나오지 않는다.
- **콘솔로 볼 때** — EC2 › Auto Scaling 그룹 › 운영 ASG의 '활동' 탭 작업 기록에서 `Terminating EC2 instance` 행의 시작 · 종료 시간을 옮긴다(+09:00로 보이면 KST 그대로). '세부 정보' 탭의 원하는 용량 · 크기 조정 한도 · 인스턴스 유형 · 상태 확인 종류 · 유예를 같이 적는다. 캡처는 인스턴스 ID를 가린다.

### 0-3. 끊긴 연결과 재연결 (Kibana)

러닝 연결은 `RunningWebSocketHandler`가 연결마다 `러닝 WebSocket 연결`, 닫힐 때마다 `러닝 WebSocket 종료 — userId=…, status=CloseStatus[code=…, reason=…]`를 INFO로 남긴다. Kibana Discover를 ES|QL로 바꿔 `filebeat-*`를 읽고, 운영은 `labels.env == "prod"`로 거른다. 시간 선택기는 질의의 날짜를 모두 덮게 넓힌다(11-25는 최근 90일).

- **질의 안 시각 문자열은 UTC다** — KST에서 9시간을 빼고 `Z`를 붙인다. 결과 열은 Kibana 시간대(KST)로 보인다.
- **`first` · `last`는 ES|QL 예약어다** — 열 이름으로 쓰면 질의가 깨진다. `t_from` · `t_to`처럼 쓴다.
- **`host.name`은 인스턴스를 가를 때만 본다** — RESULTS와 캡처에 옮기지 않는다.

1. **로그가 남은 범위** — 배포 창이 보관 범위 안인지 먼저 본다. 범위 밖이면 그 배포는 `로그 없음`이다.

```text
FROM filebeat-*
| STATS oldest = MIN(@timestamp), newest = MAX(@timestamp), docs = COUNT(*) BY labels.env
```

2. **배포 창마다 연결 · 종료** — 창은 옛 인스턴스 종료 시각(0-2)의 30분 전부터 10분 뒤까지다. 내리기 시작 → 종료(약 6분)와 PREREG 종료 창(± 10분)을 덮는다. 배포마다 `CASE`에 조건 · 이름 한 쌍을 더한다 — 11-25에는 러닝 시간대 배포 다섯 창을 돌렸다.

```text
FROM filebeat-*
| WHERE labels.env == "prod" AND message LIKE "*러닝 WebSocket*"
| EVAL kind = CASE(message LIKE "*WebSocket 연결*", "연결", message LIKE "*WebSocket 종료*", "종료", "그 밖")
| EVAL win = CASE(
    @timestamp >= "2026-11-02T09:25:00Z" AND @timestamp <= "2026-11-02T10:05:00Z", "11-02",
    @timestamp >= "2026-11-10T13:28:00Z" AND @timestamp <= "2026-11-10T14:08:00Z", "11-10",
    @timestamp >= "2026-11-15T11:04:00Z" AND @timestamp <= "2026-11-15T11:44:00Z", "11-15",
    @timestamp >= "2026-11-18T12:15:00Z" AND @timestamp <= "2026-11-18T12:55:00Z", "11-18",
    @timestamp >= "2026-11-19T12:46:00Z" AND @timestamp <= "2026-11-19T13:26:00Z", "11-19",
    null)
| WHERE win IS NOT NULL
| STATS cnt = COUNT(*), t_from = MIN(@timestamp), t_to = MAX(@timestamp) BY win, kind, host.name
| SORT win, t_from
```

종료가 있으면 코드별로 다시 센다 — `kind` 줄을 `| WHERE message LIKE "*WebSocket 종료*" | EVAL code = CASE(message LIKE "*code=1000*", "1000", message LIKE "*code=1001*", "1001", message LIKE "*code=1006*", "1006", message LIKE "*code=4001*", "4001", message LIKE "*code=4500*", "4500", "그 밖")`로, `BY`를 `win, code`로 바꾼다.

3. **하루 연결 수** — 규모를 본다. 결과의 `day`가 KST 자정(`T00:00:00.000+09:00`)인지 본다 — 09시로 나오면 UTC로 잘린 것이라 `@timestamp + 9 hours`를 자른다.

```text
FROM filebeat-*
| WHERE labels.env == "prod" AND message LIKE "*러닝 WebSocket 연결*"
| EVAL day = DATE_TRUNC(1 day, @timestamp)
| STATS conns = COUNT(*) BY day
| SORT day
```

| 코드 | 뜻 (`4001` · `4500`은 api-spec 5-C) | 셈 |
|---|---|---|
| `1006` · `1001` | 닫기 없이 끊김 · 서버가 내려감 — 배포가 끊은 연결 | 배포 끊김 |
| `4001` | 같은 사용자의 새 연결이 이어받음 — 재연결의 결과 | 재연결 |
| `4500` | 보낼 메시지가 밀려 한도를 넘음 | 따로 |
| 그 밖 (`1009` · `1011` …) | — | 따로 |

- **옛 인스턴스가 먼저 죽으면 종료 줄이 남지 않을 수 있다** — 그래서 새 인스턴스의 `러닝 WebSocket 연결` 수를 같이 센다. 종료 줄 0은 끊김 0의 증거가 아니다 — 진행 중이던 러닝은 0-4로 센다.
- **대조** — 배포가 없던 날(가능하면 같은 요일)의 같은 시각 창을 2의 질의로 센다.

### 0-4. 진행 중이던 러닝 (SQL)

```bash
# 배포마다 — 1~3만 돈다
psql "<운영 DB 읽기 접속>" -v terminated_at='<YYYY-MM-DD HH:MM:SS, KST>' -f load_test/msa/sql/prod-evidence.sql
psql "<운영 DB 읽기 접속>" -v terminated_at='<대조일 같은 시각>'         -f load_test/msa/sql/prod-evidence.sql
# 한 번 — terminated_at 없이 4~11이 돈다
psql "<운영 DB 읽기 접속>" -f load_test/msa/sql/prod-evidence.sql
```

- **1–3** — 종료 순간 진행 중이던 러닝: 기록 기준(하한) · 그 러너의 종료 상태 · 참가 기준(상한).
- **4–7** — 최근 28일 슬롯 시간 합계: 하루 최대 동시 러너, 끝내기 밀도, 다음 슬롯 시작에 겹치는 끝내기 비율, 페이스 분포. 하네스의 `profiles/` 값이 여기서 나온다.
- **8–11** — 지금 서비스 DB의 대조 기준선: I2(강제 종료 + 10분 지난 활성 참가자, 7시간 넘은 혼자 달리기) · I3 · I4 ([consistency.md](../../docs/msa/running-session-server/consistency.md) 「대조」).
- 읽기 전용 계정이 있으면 그것으로 붙는다. 파일은 `BEGIN READ ONLY`로 시작해 `ROLLBACK`으로 끝난다.

### 0-5. 같이 뽑을 값

| 무엇 | 어디서 | 쓰는 곳 |
|---|---|---|
| 18~23시 REST 비율 — `sum by (method, uri) (increase(http_server_requests_seconds_count{application="running-service"}[$__range]))`를 그 시간대로 | 운영 Prometheus | `k6/profiles/rest-mix.json` |
| 슬롯 시간 `CPUCreditBalance` 최솟값 (4주) | CloudWatch | PREREG 「실행 규칙」 16 크레딧 조건 |
| ALB · 대상 그룹 · ASG · 시작 템플릿 설정 | [platform.md](../../docs/msa/platform.md) 「확인할 운영 값」의 읽는 법 | 측정 환경을 운영과 같게 |
| 앱 재접속 · `HEALTH_CHECK` · 끝내기 재시도 · 재전송 묶음 값 | 노션 프론트 가이드 (옮겨 적는다) | [runtime.md](../../docs/msa/running-session-server/runtime.md) 「앱 재접속 정책」 → 하네스 환경 변수 |
| 요청률 · 힙 · 인스턴스 시작 시각 — Grafana `Runiverse Monitoring › JVM (Micrometer)`, Application `running-service`, Instance 드롭다운에서 그 기간에 살아 있던 인스턴스를 고른다. 배포마다 인스턴스가 바뀌어 여러 개가 남으므로, Quick Facts의 Start time을 0-2의 새 인스턴스 시각과 맞춰 본다. I/O Overview의 Rate(최근 24시간 · 30일)와 Heap used를 읽고, 캡처는 Instance 드롭다운(내부 주소)을 가린다 | 운영 Grafana | RESULTS 「0. 운영 증거」 요청률 · 힙 |
| 커넥션 풀 — 대시보드에 HikariCP 패널이 없다. `hikaricp_connections_active` · `hikaricp_connections_pending` 패널을 더한 뒤(M0) 같은 인스턴스 선택으로 읽는다. 질의는 `pool` 태그를 둘 다 받는다(`pool=~"HikariPool-1\|main"`) — 선행 수정 전 이미지(A)는 풀 이름을 정하지 않아 Hikari 기본 `HikariPool-1`, 선행 수정 이미지(A′)는 `main`(F5)이라 배포 앞뒤가 한 패널에 보인다 | 운영 Grafana | 비교군 A 풀 기준선 |

### 0-6. 적기

배포마다 [RESULTS.md](RESULTS.md) 「0. 운영 증거」에 한 줄, 4–11과 0-5의 합계는 한 번 적는다. 조회한 날짜를 같이 적는다. 끝나면 [running-session-server/README.md](../../docs/msa/running-session-server/README.md) 「지금 막힌 것」에서 이 항목을 지운다.

## 1. 준비

**측정 환경** — 운영도 dev CI 배포 대상(test EC2)도 아니다. 테스트 VPC에 따로 만들고 캠페인이 끝나면 지운다.

| 무엇 | 구성 | 이유 |
|---|---|---|
| 접속 | bastion 경유 SSH | 1차 캠페인과 같다 ([../RUNBOOK.md](../RUNBOOK.md) 「0. 준비 (한 번만)」) |
| 앱 | 비교군마다 인스턴스 둘 이상, ALB 뒤(TLS). 크기 · 수는 verification 「비교군」 | 운영은 한 대지만 B0 · B2와 비용을 맞추고 다른 인스턴스 경우를 보려고 둘 — 한 대 직결은 배포 · 재연결을 재지 못한다 |
| 이미지 | 코드가 같은 비교군은 같은 digest, 역할 · 변형은 환경 변수로만 | 이미지 차이가 결과에 섞이지 않게 |
| DB | 측정용 임시 RDS (B2는 러닝 DB용 하나 더) | test DB는 Kafka EC2 위의 PostgreSQL이라(D-10) Kafka · Connect 수치가 섞인다 |
| Redis | 측정용 EC2, AOF `everysec` (A′R2 · B2는 둘) | 운영과 같게 (D-49) |
| Kafka | 앱은 `KAFKA_CONSUMER_ENABLED=false`. Kafka가 필요한 회차는 버리는 단일 브로커 | 공유 dev/test 클러스터를 건드리지 않는다 |
| 모니터링 | 테스트 VPC에 전용 EC2 (Prometheus · Grafana · ES · Kibana) | 운영 모니터링과 VPC가 달라 붙지 못한다 |
| k6 | c 계열 EC2(버스트형 아님), digest 고정 이미지. 방을 호스트 사이에 나누지 않는다 | 생성기가 병목이 되지 않게 |
| S3 · 날씨 | test 버킷 · v2 날씨 스텁 | 실제 S3 경로를 탄다 |

- [ ] platform 「확인할 운영 값」의 ALB · 대상 그룹 · ASG · 시작 템플릿 값을 측정 환경에 같게 두고, 그 사본을 회차 기록에 남긴다 — 대상 그룹 검사 경로와 정지 대기는 비교군을 따른다(PREREG 「실행 규칙」 10)
- [ ] 앱 재접속 값이 [runtime.md](../../docs/msa/running-session-server/runtime.md) 「앱 재접속 정책」에 있다 — 없으면 SC1 · SC2 · SC3 측정 회차는 돌리지 않는다. 다른 회차는 그 표의 하네스 임시값으로 돌리고 회차 색인에 `임시값`이라 적는다
- [ ] PREREG가 동결이다 ([PREREG.md](PREREG.md) 「상태」) — 동결 전 회차는 smoke로만 쓴다
- [ ] JWT 비밀값 파일이 k6 호스트에 있다 (출력하지 않는다)
- [ ] 모든 호스트의 시계가 맞다 — `gate.sh`가 확인한다
- [ ] k6를 운영 주소로 겨누지 않는다

## 2. 한 회차

- **run_id** — 100부터 하나씩 올린다(1차 캠페인의 11~95와 겹치지 않게). 무효 회차도 번호를 쓴다. 시드와 k6는 같은 run_id · 같은 시드 값을 쓴다.
- **순서** — 블록마다 비교군을 한 번씩, 섞은 순서로 돈다. 회차 사이 쉬는 시간 · 워밍업 · 반복 수는 PREREG 「실행 규칙」.

1. 비교군 이미지 digest와 환경 변수 차이(이름만)를 적는다.
2. 상태를 비운다 — 「3. 상태 초기화」.
3. 시드 — `seed-slot.sql` · `seed-display.sql` · 필요하면 `arm-jobs.sh`.
4. `gate.sh` — 하나라도 실패하면 그 회차는 무효다.
5. 앱을 (다시) 띄운 직후면 워밍업 회차를 먼저 돌리고 버린다.
6. `k6-run.sh <시나리오> <비교군> <run_id>`.
7. 장애 시나리오면 정해진 분에 「4. 장애 주입」의 스크립트를 돌리고 시각을 초까지 적는다.
8. 「5. 무결성 검사」 → 「6. 회차마다 남길 것 · 정리」.

무효 조건은 PREREG 「판정 방식」을 따른다. 무효 회차도 RESULTS 「회차 색인」에 이유와 함께 남긴다.

## 3. 상태 초기화

| 무엇 | 어떻게 |
|---|---|
| Redis | 각 Redis에 `FLUSHALL` 뒤 `BGREWRITEAOF`. 회차 중 AOF 재작성 · bgsave가 겹쳤으면 회차 기록에 표시한다 |
| DB | 시드가 만든 데이터를 지우고 `VACUUM ANALYZE`, 또는 블록 시작마다 스냅샷 복원. B2는 러닝 DB와 메인 사본 테이블 · inbox까지 |
| S3 | 그 회차 접두어의 트랙을 지운다 |
| run_id | 새 값 — 옛 Redis 키가 남아 좌표가 중복으로 걸러지지 않게 |

## 4. 장애 주입

한 회차에 장애 하나. 장애가 없는 회차에도 toxiproxy는 경로에 둔다.

| 장애 | 스크립트 | 되돌리기 · 주의 |
|---|---|---|
| C1 서비스 DB 블랙홀 120초 · C2 DB 지연 | `chaos/toxiproxy.sh` | toxic을 지운다. C1은 `reset_peer` 변형도 돈다 |
| C3 Kafka 정지 | `chaos/kafka-throwaway.sh` | **버리는 단일 브로커에서만** — 공유 dev/test 클러스터에는 넣지 않는다 |
| C4 AI 서버 정지 | `chaos/ai-stop.sh` | 영향이 없어야 정상이다 |
| C5 날씨 2초 뒤 멈춤 | `scripts/weather-mode.sh` | 스텁을 정상 모드로 |
| C6 S3 막힘 | `chaos/s3-deny.sh` | 트랙 쓰기 거부 정책을 뗀다 |
| C7 메인 JVM kill | `chaos/main-kill.sh` | B0 · B2만 |
| SC4 Redis | `chaos/redis-kill.sh` · `chaos/redis-stop.sh` | kill은 systemd가 다시 띄운다. 회차 전 `BGREWRITEAOF` |
| SC6 결과 경로 | `chaos/kafka-restart.sh` · `chaos/connect-kill.sh` · `chaos/poison-inject.sh` · 커넥터 30분 멈춤 | 버리는 브로커에서만. 끝나면 슬롯 지연이 0으로 돌아왔는지 본다 |
| SC1 · SC2 배포 | `scripts/deploy-sim.sh` | 운영과 같은 instance refresh |
| SC11 느린 클라이언트 | `scripts/netem.sh` | k6 호스트에서 규칙을 지운다 |

## 5. 무결성 검사

- `sql/verify-integrity.sql` · `analyze/verify-tracks.py`를 그 run_id로 돌린다. B2는 회복 10분 뒤 `sql/reconcile-results.sql`까지.
- 실패한 회차는 성능 숫자로 쓰지 않는다 — RESULTS 「무결성」에 적고 기능 결함으로 따로 본다.

## 6. 회차마다 남길 것 · 정리

**회차마다** — 원시 자료는 S3에, 그 목록과 한 줄 요약은 RESULTS 「회차 색인」에 남긴다.

- [ ] k6 원시 CSV · 요약, RUNNER_DONE 로그, 무결성 출력
- [ ] 이미지 digest · git sha · PREREG sha · k6 digest
- [ ] 인스턴스 유형 · AZ, RDS 클래스, Redis 버전 · 설정, 운영 설정 사본
- [ ] 장애 · 배포 시각(KST, 초까지), 환경 변수 차이(이름만)
- [ ] Grafana 화면(회차 시간 범위), CPU 크레딧

**캠페인이 끝나면**

- [ ] 측정용 RDS · Redis · k6 · 모니터링 EC2와 그 보안 그룹을 지운다
- [ ] `lag-prober` 커넥터와 그 복제 슬롯을 지운다 — 슬롯이 남으면 WAL이 쌓인다
- [ ] test 버킷에 남은 트랙을 지운다
- [ ] 버리는 Kafka 브로커를 지운다
