package com.ktb.community.service;

import com.ktb.community.repository.UserTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 토큰 정리 배치 서비스
 * - 만료된 Refresh Token 자동 삭제 (expires_at < NOW())
 * - 매일 새벽 3시 실행
 *
 * LLD.md Section 6.1, PRD.md NFR-REL-003 참조
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupBatchService {

    private final UserTokenRepository userTokenRepository;

    /**
     * 만료된 토큰 정리 배치 작업
     * - 스케줄: 매일 새벽 3시 (CRON: 0 0 3 * * ?)
     * - TTL 만료 토큰 삭제 (DB Hard Delete)
     * - 단일 JPQL DELETE 쿼리로 처리 (원자적 트랜잭션)
     *
     * 참고: ImageCleanupBatchService와 동일 시간대 실행
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("[Batch] 만료된 토큰 정리 배치 시작");
        long startTime = System.currentTimeMillis();

        LocalDateTime now = LocalDateTime.now();

        try {
            // JPQL DELETE로 만료된 토큰 일괄 삭제
            int deletedCount = userTokenRepository.deleteExpiredTokens(now);

            long elapsedTime = System.currentTimeMillis() - startTime;

            if (deletedCount > 0) {
                log.info("[Batch] 만료된 토큰 정리 완료: 삭제={}개, 소요시간={}ms",
                        deletedCount, elapsedTime);
            } else {
                log.info("[Batch] 삭제할 만료 토큰 없음, 소요시간={}ms", elapsedTime);
            }

        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.error("[Batch] 만료된 토큰 정리 실패: error={}, 소요시간={}ms",
                     e.getMessage(), elapsedTime, e);
            // 예외를 다시 던지지 않음 (다음 스케줄 실행 보장)
        }
    }
}
