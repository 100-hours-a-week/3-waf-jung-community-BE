-- =============================================
-- 03. 더미 데이터 삽입 스크립트 (래퍼)
-- =============================================
-- 이 파일은 dummy_data.sql을 참조합니다.
-- 실제 데이터는 dummy_data.sql (4.8MB)에 있습니다.
--
-- 데이터 규모:
--   - 유저: 1,200명
--   - 게시글: 3,000개
--   - 댓글: 30,000~45,000개 (게시글당 10~15개)
--   - 좋아요: 1,200개 (post_id=3000에 집중)
-- =============================================
-- 실행 방법:
--   1. run_all.sh 사용 (권장)
--   2. 직접 실행: mysql -u root -p community < dummy_data.sql
--   3. Docker: docker exec -i mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD community < dummy_data.sql
-- =============================================

USE community;

-- SOURCE 명령어로 실제 데이터 파일 실행
-- 주의: Docker stdin 파이프로는 SOURCE가 동작하지 않습니다.
-- run_all.sh 또는 직접 mysql 클라이언트에서 실행하세요.

SOURCE dummy_data.sql;
