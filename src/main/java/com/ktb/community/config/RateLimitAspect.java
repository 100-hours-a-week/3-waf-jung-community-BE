package com.ktb.community.config;

import com.ktb.community.enums.ErrorCode;
import com.ktb.community.exception.BusinessException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate Limiting AOP
 * @RateLimit 어노테이션이 붙은 메서드의 호출 빈도 제한
 * 
 * - Bucket4j 사용 (Token Bucket 알고리즘)
 * - IP + 사용자ID 기반 제한
 * - 인메모리 저장 (추후 Redis 전환 가능)
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    // 부하테스트용 Rate Limit 비활성화 (테스트 후 true로 복원 필요)
    private boolean rateLimitEnabled = false;

    /**
     * 클라이언트별 Bucket 캐시 (Caffeine)
     * - 자동 만료: 10분 미사용 시 삭제
     * - 최대 크기: 10,000개
     * - Key: FQCN.methodName:IP 또는 FQCN.methodName:IP:userId
     * - Value: Bucket4j Bucket
     */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build();
    
    /**
     * @RateLimit 어노테이션이 붙은 메서드 가로채기
     * 
     * @param pjp 메서드 실행 지점
     * @param rateLimit @RateLimit 어노테이션
     * @return 메서드 실행 결과
     * @throws Throwable 메서드 실행 중 예외
     */
    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        // 환경변수로 Rate Limit 비활성화 가능
        if (!rateLimitEnabled) {
            return pjp.proceed();
        }

        String clientKey = getClientKey(pjp);
        int requestsPerMinute = rateLimit.requestsPerMinute();
        
        // 클라이언트별 Bucket 생성 또는 가져오기
        Bucket bucket = buckets.get(clientKey, k -> createBucket(requestsPerMinute));
        
        // 토큰 획득 시도
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for client: {} (limit: {}/min)", 
                clientKey, requestsPerMinute);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        
        log.debug("Rate limit check passed for client: {}", clientKey);
        return pjp.proceed();
    }
    
    /**
     * Bucket4j Bucket 생성
     * Token Bucket 알고리즘 적용
     * 
     * @param requestsPerMinute 분당 허용 요청 수
     * @return Bucket
     */
    private Bucket createBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(
            requestsPerMinute, 
            Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
    
    /**
     * 클라이언트 식별 키 생성
     * - 엔드포인트별 격리: FQCN.methodName 포함
     * - 인증된 사용자: FQCN.methodName:IP:userId
     * - 비인증 사용자: FQCN.methodName:IP
     * - 예: "com.ktb.community.controller.AuthController.login:192.168.1.1:user@example.com"
     * 
     * @param pjp 메서드 실행 지점
     * @return 클라이언트 키
     */
    private String getClientKey(ProceedingJoinPoint pjp) {
        String methodName = pjp.getSignature().getDeclaringTypeName() 
                          + "." + pjp.getSignature().getName();
        HttpServletRequest request = getCurrentRequest();
        String ip = getClientIp(request);
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        String userKey;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            userKey = ip + ":" + auth.getName(); // IP:userId
        } else {
            userKey = ip; // 비인증 사용자는 IP만
        }
        
        return methodName + ":" + userKey; // FQCN.methodName:IP 또는 FQCN.methodName:IP:userId
    }
    
    /**
     * 현재 HTTP 요청 가져오기
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }
    
    /**
     * 클라이언트 IP 추출
     * Proxy/Load Balancer 고려 (X-Forwarded-For 헤더 우선)
     * 
     * @param request HTTP 요청
     * @return 클라이언트 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // X-Forwarded-For에 여러 IP가 있을 경우 첫 번째 IP 사용
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}
