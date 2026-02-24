-- =============================================
-- 01. 전체 테이블 삭제 스크립트
-- 모든 테이블과 데이터를 삭제합니다.
-- =============================================
-- 실행: mysql -u root -p community < 01_drop_all.sql
-- Docker: docker exec -i mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD community < 01_drop_all.sql
-- =============================================

USE community;

-- FK 제약조건 비활성화 (삭제 순서 무관하게)
SET FOREIGN_KEY_CHECKS = 0;

-- 모든 테이블 삭제 (의존성 역순)
DROP TABLE IF EXISTS user_tokens;
DROP TABLE IF EXISTS post_likes;
DROP TABLE IF EXISTS post_images;
DROP TABLE IF EXISTS comments;
DROP TABLE IF EXISTS post_stats;
DROP TABLE IF EXISTS posts;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS images;

-- FK 제약조건 다시 활성화
SET FOREIGN_KEY_CHECKS = 1;

-- 완료 메시지
SELECT 'All tables dropped successfully!' AS status;
