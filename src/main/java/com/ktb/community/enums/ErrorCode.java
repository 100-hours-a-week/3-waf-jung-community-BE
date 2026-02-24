package com.ktb.community.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드 정의
 * 형식: {DOMAIN}-{NUMBER}
 * 예: AUTH-001, USER-002, POST-001
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    
    // ==================== 인증 (AUTH) ====================
    INVALID_CREDENTIALS("AUTH-001", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("AUTH-002", "Invalid or expired token", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("AUTH-003", "Token has expired", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("AUTH-004", "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    
    // ==================== 사용자 (USER) ====================
    USER_NOT_FOUND("USER-001", "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("USER-002", "Email already exists", HttpStatus.CONFLICT),
    NICKNAME_ALREADY_EXISTS("USER-003", "Nickname already exists", HttpStatus.CONFLICT),
    INVALID_PASSWORD_POLICY("USER-004", "Password does not meet policy requirements", HttpStatus.BAD_REQUEST),
    ACCOUNT_INACTIVE("USER-005", "Account is inactive", HttpStatus.UNAUTHORIZED),
    PASSWORD_MISMATCH("USER-006", "Passwords do not match", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_ACCESS("USER-007", "Not authorized to access this resource", HttpStatus.FORBIDDEN),
    
    // ==================== 게시글 (POST) - Phase 3 대비 ====================
    POST_NOT_FOUND("POST-001", "Post not found", HttpStatus.NOT_FOUND),
    POST_OWNER_MISMATCH("POST-002", "Not authorized to modify this post", HttpStatus.FORBIDDEN),
    POST_ALREADY_DELETED("POST-003", "Post has already been deleted", HttpStatus.BAD_REQUEST),
    INVALID_POST_STATUS("POST-004", "Invalid post status", HttpStatus.BAD_REQUEST),
    
    // ==================== 댓글 (COMMENT) - Phase 3 대비 ====================
    COMMENT_NOT_FOUND("COMMENT-001", "Comment not found", HttpStatus.NOT_FOUND),
    COMMENT_OWNER_MISMATCH("COMMENT-002", "Not authorized to modify this comment", HttpStatus.FORBIDDEN),
    COMMENT_ALREADY_DELETED("COMMENT-003", "Comment has already been deleted", HttpStatus.BAD_REQUEST),
    
    // ==================== 좋아요 (LIKE) - Phase 3 대비 ====================
    ALREADY_LIKED("LIKE-001", "Post already liked", HttpStatus.CONFLICT),
    LIKE_NOT_FOUND("LIKE-002", "Like not found", HttpStatus.NOT_FOUND),
    
    // ==================== 이미지 (IMAGE) - Phase 4 대비 ====================
    IMAGE_NOT_FOUND("IMAGE-001", "Image not found", HttpStatus.NOT_FOUND),
    FILE_TOO_LARGE("IMAGE-002", "File size exceeds limit", HttpStatus.PAYLOAD_TOO_LARGE),
    INVALID_FILE_TYPE("IMAGE-003", "Invalid file type", HttpStatus.BAD_REQUEST),
    INVALID_IMAGE_URL("IMAGE-004", "Invalid image URL format", HttpStatus.BAD_REQUEST),
    
    // ==================== 공통 (COMMON) ====================
    INVALID_INPUT("COMMON-001", "Invalid input data", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("COMMON-002", "Resource not found", HttpStatus.NOT_FOUND),
    RESOURCE_CONFLICT("COMMON-003", "Resource conflict", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS("COMMON-004", "Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_SERVER_ERROR("COMMON-999", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
    
    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
