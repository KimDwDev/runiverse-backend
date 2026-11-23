-- 0단계 운영 증거 — 운영 DB에서 읽기만 하고(READ ONLY 트랜잭션) 합계만 낸다.
-- 사용자 ID·좌표·방 번호는 결과에 나오지 않는다.
-- 절차: load_test/msa/RUNBOOK.md 「0. 운영 증거」 → 결과 칸: load_test/msa/RESULTS.md 「0. 운영 증거」
--
-- 배포마다 (1~3 — 옛 인스턴스가 끝난 순간 진행 중이던 러닝):
--   psql "<운영 DB 읽기 접속>" -v terminated_at='2026-11-20 02:40:00' -f load_test/msa/sql/prod-evidence.sql
--   terminated_at  KST 벽시계 — DB의 *_at은 시간대 없는 KST다(erd 「0. 공통 규칙」). ASG 기록은 UTC라 9시간을 더한다.
--   대조: 배포가 없던 날(가능하면 같은 요일)의 같은 시각으로 한 번 더 돌린다.
-- 한 번 (4~11 — 배포와 무관한 합계, terminated_at 없이):
--   psql "<운영 DB 읽기 접속>" -f load_test/msa/sql/prod-evidence.sql
--   days               4~7을 볼 기간, 기본 28일
--   force_finish_hours 강제 종료 유예, 기본 6 (match.force-finish-offset)
--   track_ttl_hours    트랙 키 수명, 기본 7 (running-track.ttl)
--   tz                 DB 서버 now()를 KST로 바꿀 시간대, 기본 Asia/Seoul

\set ON_ERROR_STOP on

\if :{?days}
\else
  \set days 28
\endif
\if :{?force_finish_hours}
\else
  \set force_finish_hours 6
\endif
\if :{?track_ttl_hours}
\else
  \set track_ttl_hours 7
\endif
\if :{?tz}
\else
  \set tz 'Asia/Seoul'
\endif

BEGIN READ ONLY;
SET LOCAL statement_timeout = '60s';

\if :{?terminated_at}

-- 1. 종료 순간 트랙이 이어지던 러닝 (기록 기준 — 하한)
--    start_at·end_at은 트랙의 첫 점·마지막 반영 점이다. end_at은 목표 지점에서 잘리고,
--    유효 기록이 생기지 않은 러닝은 빠지므로 실제보다 적게 센다.
\echo '1. 종료 순간 진행 중 러닝 (기록 기준, 방 종류별)'
SELECT coalesce(rr.type, '합계')           AS room_type,
       count(*)                             AS runners,
       count(DISTINCT r.running_room_id)    AS rooms
FROM running_records r
JOIN running_rooms rr ON rr.running_room_id = r.running_room_id
WHERE r.start_at <= :'terminated_at'::timestamp
  AND r.end_at   >= :'terminated_at'::timestamp
GROUP BY ROLLUP (rr.type)
ORDER BY rr.type NULLS LAST;

-- 2. 1의 러너가 어떻게 끝났나 — 조기 종료(RUNNING_LEFT_*) 비율을 대조일과 견준다
\echo '2. 1의 러너의 종료 상태'
SELECT p.status,
       count(*) AS runners
FROM running_records r
JOIN running_room_sessions s ON s.running_room_id = r.running_room_id AND s.user_id = r.user_id
JOIN running_players p ON p.running_player_id = s.running_player_id
WHERE r.start_at <= :'terminated_at'::timestamp
  AND r.end_at   >= :'terminated_at'::timestamp
GROUP BY p.status
ORDER BY runners DESC;

-- 3. 종료 순간 러닝 단계에 있던 참가자 (참가 기준 — 상한)
--    방 시작 시각 이후이고 신청이 그 뒤에 끝난, 실제로 뛰기 시작한 참가자다(미출석은 MATCHED_LEFT_*라 빠진다).
--    기록 없이 끝난 러닝까지 들어가고, 시작 시각보다 늦게 붙은 사람도 들어가 실제보다 많게 센다.
\echo '3. 종료 순간 러닝 단계 참가자 (참가 기준, 방 종류별)'
SELECT coalesce(rr.type, '합계')           AS room_type,
       count(*)                             AS runners,
       count(*) FILTER (WHERE NOT EXISTS (
           SELECT 1 FROM running_records r
           WHERE r.running_room_id = s.running_room_id AND r.user_id = s.user_id)) AS runners_without_record
FROM running_players p
JOIN running_room_sessions s ON s.running_player_id = p.running_player_id
JOIN running_rooms rr ON rr.running_room_id = s.running_room_id
WHERE p.status IN ('COMPLETED', 'RUNNING_LEFT_PENALTY', 'RUNNING_LEFT_NO_PENALTY')
  AND rr.start_at  <= :'terminated_at'::timestamp
  AND p.deleted_at >= :'terminated_at'::timestamp
GROUP BY ROLLUP (rr.type)
ORDER BY rr.type NULLS LAST;

\else

-- 4. 슬롯 시간(18:00~24:00) 하루 최대 동시 러너 — 하네스 부하 크기와 SC13 규모의 기준
--    기록의 [start_at, end_at)을 시작 +1·끝 -1 사건으로 펴서 누적한다. 같은 시각이면 끝을 먼저 뺀다.
\echo '4. 슬롯 시간 하루 최대 동시 러너 (최근 days일)'
WITH since AS (
    SELECT date_trunc('day', now() AT TIME ZONE :'tz') - make_interval(days => :days) AS t
), ev AS (
    SELECT r.start_at AS t, 1 AS d FROM running_records r, since WHERE r.start_at >= since.t
    UNION ALL
    SELECT r.end_at, -1 FROM running_records r, since WHERE r.start_at >= since.t
), live AS (
    SELECT t, sum(d) OVER (ORDER BY t, d ROWS UNBOUNDED PRECEDING) AS runners FROM ev
), daily AS (
    SELECT t::date AS day, max(runners) AS peak
    FROM live
    WHERE t::time >= time '18:00'
    GROUP BY t::date
)
SELECT count(*)                                             AS days_with_runs,
       percentile_disc(0.5)  WITHIN GROUP (ORDER BY peak)   AS peak_p50,
       percentile_disc(0.95) WITHIN GROUP (ORDER BY peak)   AS peak_p95,
       max(peak)                                            AS peak_max
FROM daily;

-- 5. 끝내기 밀도 — 기록을 확정한 시각(created_at) 기준, 슬롯 시간만. SC9 끝내기 몰림의 기준
\echo '5. 슬롯 시간 끝내기 밀도 (분당 · 10초당, 끝내기가 있던 칸만)'
WITH since AS (
    SELECT date_trunc('day', now() AT TIME ZONE :'tz') - make_interval(days => :days) AS t
), fin AS (
    SELECT r.created_at AS t FROM running_records r, since
    WHERE r.created_at >= since.t AND r.created_at::time >= time '18:00'
), per_min AS (
    SELECT date_trunc('minute', t) AS m, count(*) AS n FROM fin GROUP BY 1
), per_10s AS (
    SELECT date_trunc('minute', t) + floor(extract(second FROM t) / 10) * interval '10 seconds' AS b,
           count(*) AS n
    FROM fin GROUP BY 1
)
SELECT (SELECT count(*) FROM fin)                                                  AS finishes,
       (SELECT percentile_disc(0.5)  WITHIN GROUP (ORDER BY n) FROM per_min)       AS per_min_p50,
       (SELECT percentile_disc(0.95) WITHIN GROUP (ORDER BY n) FROM per_min)       AS per_min_p95,
       (SELECT percentile_disc(0.99) WITHIN GROUP (ORDER BY n) FROM per_min)       AS per_min_p99,
       (SELECT max(n) FROM per_min)                                                AS per_min_max,
       (SELECT max(n) FROM per_10s)                                                AS per_10s_max;

-- 6. 매칭 방 끝내기가 다음 슬롯 시작(19~22시 정각) ±5분 안에 몰리는 비율 — SC8(겹치는 코호트)의 근거
--    슬롯 시작 시각은 match.allowed-first/last-start-time(18:00~22:00, 1시간 간격)이다.
\echo '6. 매칭 방 끝내기 중 다음 슬롯 시작 ±5분 안의 비율 (목표 거리별)'
WITH since AS (
    SELECT date_trunc('day', now() AT TIME ZONE :'tz') - make_interval(days => :days) AS t
), fin AS (
    SELECT rr.target_distance, r.created_at AS t
    FROM running_records r
    JOIN running_rooms rr ON rr.running_room_id = r.running_room_id, since
    WHERE rr.type = 'MATCH' AND r.created_at >= since.t
)
SELECT target_distance,
       count(*) AS match_finishes,
       round(100.0 * count(*) FILTER (
           WHERE abs(extract(epoch FROM t - date_trunc('hour', t + interval '30 minutes'))) <= 300
             AND extract(hour FROM date_trunc('hour', t + interval '30 minutes')) BETWEEN 19 AND 22
       ) / nullif(count(*), 0), 1) AS pct_within_5min_of_slot
FROM fin
GROUP BY target_distance
ORDER BY target_distance;

-- 7. 페이스 · 거리 분포 — 하네스 profiles/pace.json의 원자료 (방 종류 · 목표 거리별)
\echo '7. 페이스(초/km) · 움직인 시간(초) 분포'
WITH since AS (
    SELECT date_trunc('day', now() AT TIME ZONE :'tz') - make_interval(days => :days) AS t
)
SELECT rr.type                                                                     AS room_type,
       rr.target_distance,
       count(*)                                                                    AS records,
       percentile_disc(0.1) WITHIN GROUP (ORDER BY r.avg_pace)                     AS pace_p10,
       percentile_disc(0.5) WITHIN GROUP (ORDER BY r.avg_pace)                     AS pace_p50,
       percentile_disc(0.9) WITHIN GROUP (ORDER BY r.avg_pace)                     AS pace_p90,
       percentile_disc(0.5) WITHIN GROUP (ORDER BY r.total_duration)               AS duration_p50,
       percentile_disc(0.5) WITHIN GROUP (ORDER BY r.total_distance)               AS distance_p50
FROM running_records r
JOIN running_rooms rr ON rr.running_room_id = r.running_room_id, since
WHERE r.created_at >= since.t
GROUP BY rr.type, rr.target_distance
HAVING count(*) >= 5   -- 다섯 건 아래 칸은 내지 않는다(합계만 남긴다)
ORDER BY rr.type, rr.target_distance NULLS FIRST;

-- 8~11. 지금 상태 대조의 기준선 — consistency.md 「대조」의 검사를 분리 전 서비스 DB에서 센다.
--    활성 = running_players.deleted_at IS NULL(erd running_players 「활성 신청 판정」).
\echo '8. I2 — 강제 종료 시각 + 10분이 지나도 활성인 참가자 (매칭 · 초대)'
SELECT count(*) AS active_past_force_finish
FROM running_players p
JOIN running_room_sessions s ON s.running_player_id = p.running_player_id
JOIN running_rooms rr ON rr.running_room_id = s.running_room_id
WHERE p.deleted_at IS NULL
  AND rr.type IN ('MATCH', 'INVITE')
  AND rr.start_at + make_interval(hours => :force_finish_hours) + interval '10 minutes'
      < now() AT TIME ZONE :'tz';

\echo '9. I2 — 트랙 키 수명이 지나도 RUNNING인 혼자 달리기'
SELECT count(*) AS solo_running_past_ttl
FROM running_players p
JOIN running_room_sessions s ON s.running_player_id = p.running_player_id
JOIN running_rooms rr ON rr.running_room_id = s.running_room_id
WHERE p.deleted_at IS NULL
  AND p.status = 'RUNNING'
  AND rr.type = 'SOLO'
  AND rr.start_at + make_interval(hours => :track_ttl_hours) < now() AT TIME ZONE :'tz';

\echo '10. I3 — 끝난 방에 남은 활성 참가자'
SELECT rr.status,
       count(*) AS active_in_closed_room
FROM running_players p
JOIN running_room_sessions s ON s.running_player_id = p.running_player_id
JOIN running_rooms rr ON rr.running_room_id = s.running_room_id
WHERE p.deleted_at IS NULL
  AND rr.status IN ('FINISHED', 'CANCELLED')
GROUP BY rr.status;

\echo '11. I4 — 활성 신청이 둘 이상인 사용자'
SELECT count(*) AS users_with_multiple_active
FROM (
    SELECT user_id FROM running_players WHERE deleted_at IS NULL GROUP BY user_id HAVING count(*) > 1
) x;

\endif

ROLLBACK;
