-- 운영은 배포 SQL이 두 플랫폼 행을 미리 넣는다.
-- E2E는 앱이 기동하며 테이블을 새로 만들므로(create-drop) 앱이 뜬 뒤에 같은 일을 한다.
-- 감사 컬럼은 앱이 채우지 않아 직접 넣는다 — 저장 기준이 KST 벽시계다
INSERT INTO app_versions (platform, minimum_version, latest_version, created_at, updated_at)
VALUES ('IOS', '1.0.0', '1.1.0', now() AT TIME ZONE 'Asia/Seoul', now() AT TIME ZONE 'Asia/Seoul'),
       ('ANDROID', '1.1.0', '1.2.0', now() AT TIME ZONE 'Asia/Seoul', now() AT TIME ZONE 'Asia/Seoul');
