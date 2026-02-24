-- =============================================
-- 04. 데이터 검증 스크립트
-- 더미 데이터 삽입 후 무결성 확인
-- =============================================
-- 실행: mysql -u root -p community < 04_verify.sql
-- Docker: docker exec -i mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD community < 04_verify.sql
-- =============================================

USE community;

-- 1. 전체 데이터 개수 확인
SELECT '=== 전체 데이터 개수 ===' AS info;
SELECT
    'users' AS table_name,
    COUNT(*) AS count,
    '1200 예상' AS expected
FROM users
UNION ALL
SELECT
    'posts',
    COUNT(*),
    '3000 예상'
FROM posts
UNION ALL
SELECT
    'post_stats',
    COUNT(*),
    '3000 예상'
FROM post_stats
UNION ALL
SELECT
    'comments',
    COUNT(*),
    '30000~45000 예상'
FROM comments
UNION ALL
SELECT
    'post_likes',
    COUNT(*),
    '1200 예상'
FROM post_likes;

-- 2. 테이블별 상세 정보
SELECT '\n=== 테이블 상태 ===' AS info;
SELECT
    TABLE_NAME,
    TABLE_ROWS,
    ROUND(DATA_LENGTH / 1024 / 1024, 2) AS data_mb,
    ROUND(INDEX_LENGTH / 1024 / 1024, 2) AS index_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'community'
ORDER BY TABLE_NAME;

-- 3. post_id=3000 게시글 확인 (좋아요 테스트용)
SELECT '\n=== post_id=3000 게시글 정보 ===' AS info;
SELECT
    p.post_id,
    p.post_title,
    p.post_status,
    u.nickname AS author,
    ps.like_count,
    ps.comment_count,
    ps.view_count,
    p.created_at
FROM posts p
LEFT JOIN users u ON p.user_id = u.user_id
LEFT JOIN post_stats ps ON p.post_id = ps.post_id
WHERE p.post_id = 3000;

-- 4. post_id=3000의 좋아요 개수 확인
SELECT '\n=== post_id=3000 좋아요 개수 ===' AS info;
SELECT
    post_id,
    COUNT(*) AS actual_likes,
    '1200 예상' AS expected
FROM post_likes
WHERE post_id = 3000
GROUP BY post_id;

-- 5. 데이터 무결성 검증: like_count vs 실제 좋아요
SELECT '\n=== 데이터 무결성 검증 ===' AS info;
SELECT
    ps.post_id,
    ps.like_count AS stats_like_count,
    COUNT(pl.user_id) AS actual_like_count,
    CASE
        WHEN ps.like_count = COUNT(pl.user_id) THEN '✓ 일치'
        ELSE '✗ 불일치'
    END AS status
FROM post_stats ps
LEFT JOIN post_likes pl ON ps.post_id = pl.post_id
WHERE ps.post_id = 3000
GROUP BY ps.post_id, ps.like_count;

-- 6. 샘플 유저 확인 (처음 5명)
SELECT '\n=== 샘플 유저 (처음 5명) ===' AS info;
SELECT user_id, email, nickname, user_status, created_at
FROM users
ORDER BY user_id
LIMIT 5;

-- 7. 샘플 게시글 확인 (처음 5개)
SELECT '\n=== 샘플 게시글 (처음 5개) ===' AS info;
SELECT
    p.post_id,
    LEFT(p.post_title, 20) AS title_preview,
    u.nickname AS author,
    ps.like_count,
    ps.comment_count,
    p.created_at
FROM posts p
LEFT JOIN users u ON p.user_id = u.user_id
LEFT JOIN post_stats ps ON p.post_id = ps.post_id
ORDER BY p.post_id
LIMIT 5;

SELECT '\n=== 검증 완료 ===' AS info;
