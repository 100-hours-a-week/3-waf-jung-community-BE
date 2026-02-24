-- KTB Community MySQL 초기화 스크립트
-- Version: 1.0
-- MySQL 8.0+ 호환

-- 데이터베이스 생성 (필요시)
CREATE DATABASE IF NOT EXISTS community
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE community;

-- 기존 테이블 삭제 (의존성 역순)
DROP TABLE IF EXISTS user_tokens;
DROP TABLE IF EXISTS post_likes;
DROP TABLE IF EXISTS post_images;
DROP TABLE IF EXISTS comments;
DROP TABLE IF EXISTS post_stats;
DROP TABLE IF EXISTS posts;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS images;

-- 1. images (의존성 없음)
CREATE TABLE images (
    image_id BIGINT AUTO_INCREMENT,
    image_url VARCHAR(2048) NOT NULL,
    file_size INT UNSIGNED,
    original_filename VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL DEFAULT NULL,

    PRIMARY KEY(image_id),
    KEY idx_images_expires (expires_at)
);

-- 2. users (images 참조)
CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    user_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    image_id BIGINT DEFAULT NULL,

    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_nickname (nickname),
    KEY idx_users_status (user_status),
    CONSTRAINT fk_users_profile_image
        FOREIGN KEY (image_id) REFERENCES images(image_id)
        ON DELETE SET NULL
        ON UPDATE RESTRICT,
    CONSTRAINT chk_users_nickname_len CHECK (CHAR_LENGTH(nickname) <= 10),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (user_status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT chk_users_email_trim_lower CHECK (email = LOWER(TRIM(email))),
    CONSTRAINT chk_users_email_no_ws CHECK (email NOT REGEXP '\\s')
);

-- 3. posts (users 참조)
CREATE TABLE posts (
    post_id BIGINT AUTO_INCREMENT,
    post_title VARCHAR(100) NOT NULL,
    post_content LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    post_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    user_id BIGINT NOT NULL,

    PRIMARY KEY (post_id),
    KEY idx_posts_created (created_at DESC),
    KEY idx_posts_user_created (user_id, created_at DESC),
    CONSTRAINT fk_posts_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT chk_posts_title_len CHECK (CHAR_LENGTH(post_title) <= 27),
    CONSTRAINT chk_posts_status CHECK (post_status IN ('ACTIVE', 'DELETED', 'DRAFT'))
);

-- 4. post_stats (posts 참조)
CREATE TABLE post_stats (
    post_id BIGINT,
    like_count INT UNSIGNED NOT NULL DEFAULT 0,
    comment_count INT UNSIGNED NOT NULL DEFAULT 0,
    view_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_stats_post
        FOREIGN KEY (post_id) REFERENCES posts(post_id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
);

-- 5. comments (posts, users 참조)
CREATE TABLE comments (
    comment_id BIGINT AUTO_INCREMENT,
    comment_content VARCHAR(600) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    comment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    PRIMARY KEY (comment_id),
    KEY idx_comments_post_created (post_id, created_at, comment_id),
    CONSTRAINT fk_comments_post
        FOREIGN KEY (post_id) REFERENCES posts(post_id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT chk_comments_len CHECK (CHAR_LENGTH(comment_content) <= 200),
    CONSTRAINT chk_comments_status CHECK (comment_status IN ('ACTIVE', 'DELETED'))
);

-- 6. post_images (posts, images 참조)
CREATE TABLE post_images (
    post_id BIGINT NOT NULL,
    image_id BIGINT NOT NULL,
    display_order TINYINT UNSIGNED NOT NULL DEFAULT 1,

    PRIMARY KEY (post_id, image_id),
    UNIQUE KEY uq_post_images_order (post_id, display_order),
    CONSTRAINT fk_post_images_post
        FOREIGN KEY (post_id) REFERENCES posts(post_id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_post_images_image
        FOREIGN KEY (image_id) REFERENCES images(image_id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
);

-- 7. post_likes (users, posts 참조)
CREATE TABLE post_likes (
    like_id BIGINT AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (like_id),
    UNIQUE KEY uq_user_post (user_id, post_id),
    KEY idx_post_likes_post (post_id),
    CONSTRAINT fk_post_likes_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_post_likes_post
        FOREIGN KEY (post_id) REFERENCES posts(post_id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
);

-- 8. user_tokens (users 참조)
CREATE TABLE user_tokens (
    user_token_id BIGINT NOT NULL AUTO_INCREMENT,
    token VARCHAR(512) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_agent VARCHAR(255),
    ip_address VARCHAR(45),
    user_id BIGINT NOT NULL,

    PRIMARY KEY (user_token_id),
    UNIQUE KEY uq_user_tokens_token (token),
    KEY idx_user_tokens_user (user_id),
    KEY idx_tokens_expires (expires_at),
    CONSTRAINT fk_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
);

-- 완료 메시지
SELECT 'Database initialized successfully!' AS status;
