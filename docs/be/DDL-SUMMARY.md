---
name: ddl-summary
description: DB 테이블 구조 요약. 컬럼/FK 관계 빠른 파악용.
---

# DB 스키마 요약

## 테이블 목록 (8개)

| 테이블 | 주요 컬럼 | 관계 |
|--------|----------|------|
| users | user_id, email, nickname, password_hash, user_status, image_id | 1:N posts, comments, likes |
| posts | post_id, user_id, post_title, post_content, post_status | 1:1 post_stats, 1:N comments |
| comments | comment_id, post_id, user_id, comment_content, comment_status | N:1 posts, users |
| post_likes | like_id, user_id, post_id | N:1 users, posts |
| images | image_id, image_url, expires_at, file_size | M:N posts (via post_images) |
| post_images | post_id, image_id, display_order | 브릿지 테이블 |
| post_stats | post_id, like_count, comment_count, view_count | 1:1 posts |
| user_tokens | user_token_id, token, user_id, expires_at | N:1 users |

## 주요 제약조건

| 타입 | 대상 | 설명 |
|------|------|------|
| UNIQUE | email, nickname | 중복 방지 |
| UNIQUE | (user_id, post_id) in post_likes | 좋아요 중복 방지 |
| CHECK | nickname ≤ 10자, comment ≤ 200자 | 길이 제한 |
| CHECK | user_status IN ('ACTIVE', 'INACTIVE', 'DELETED') | Enum 검증 |

## Soft Delete 정책

| 테이블 | 컬럼 | 상태값 |
|--------|------|--------|
| users | user_status | ACTIVE, INACTIVE, DELETED |
| posts | post_status | ACTIVE, DELETED, DRAFT |
| comments | comment_status | ACTIVE, DELETED |

## 주요 인덱스

- `idx_posts_created`: created_at DESC (최신 글)
- `idx_posts_user_created`: (user_id, created_at DESC)
- `idx_comments_post_created`: (post_id, created_at, comment_id)
- `idx_images_expires`: expires_at (고아 이미지 정리)

→ **상세 DDL**: `docs/be/DDL.md` 참조
