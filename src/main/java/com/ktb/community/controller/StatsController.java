package com.ktb.community.controller;

import com.ktb.community.dto.ApiResponse;
import com.ktb.community.enums.CommentStatus;
import com.ktb.community.enums.PostStatus;
import com.ktb.community.enums.UserStatus;
import com.ktb.community.repository.CommentRepository;
import com.ktb.community.repository.PostRepository;
import com.ktb.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 통계 컨트롤러
 * 랜딩페이지용 플랫폼 통계 제공
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    /**
     * 플랫폼 통계 조회
     * GET /stats
     *
     * 응답:
     * - totalPosts: ACTIVE 상태 게시글 수
     * - totalUsers: ACTIVE 상태 사용자 수
     * - totalComments: ACTIVE 상태 댓글 수
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats() {
        long totalPosts = postRepository.countByPostStatus(PostStatus.ACTIVE);
        long totalUsers = userRepository.countByUserStatus(UserStatus.ACTIVE);
        long totalComments = commentRepository.countByCommentStatus(CommentStatus.ACTIVE);

        Map<String, Long> stats = Map.of(
                "totalPosts", totalPosts,
                "totalUsers", totalUsers,
                "totalComments", totalComments
        );

        return ResponseEntity.ok(ApiResponse.success("get_stats_success", stats));
    }
}
