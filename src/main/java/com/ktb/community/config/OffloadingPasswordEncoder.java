package com.ktb.community.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt 연산을 전용 Platform Thread Pool로 offload하는 PasswordEncoder Decorator.
 *
 * <p>Virtual Thread 환경에서 BCrypt(CPU-bound ~100ms)가 carrier thread를 점유(pin)하는 문제를 해결한다.
 * {@code ExecutorService.submit()} + {@code Future.get()} 패턴으로 virtual thread가
 * carrier에서 unmount되어 다른 virtual thread(read 요청 등)가 carrier를 사용할 수 있다.</p>
 *
 * <p>Fail-open: 큐 초과(RejectedExecutionException) 시 caller thread에서 직접 실행하여
 * graceful degradation을 보장한다.</p>
 */
public class OffloadingPasswordEncoder implements PasswordEncoder {

    private static final Logger log = LoggerFactory.getLogger(OffloadingPasswordEncoder.class);

    private final PasswordEncoder delegate;
    private final ExecutorService executor;

    public OffloadingPasswordEncoder(PasswordEncoder delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        try {
            return executor.submit(() -> delegate.encode(rawPassword)).get();
        } catch (RejectedExecutionException e) {
            log.warn("BCrypt executor queue full, falling back to caller thread");
            return delegate.encode(rawPassword);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BCrypt encode interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("BCrypt encode failed", e);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        try {
            return executor.submit(() -> delegate.matches(rawPassword, encodedPassword)).get();
        } catch (RejectedExecutionException e) {
            log.warn("BCrypt executor queue full, falling back to caller thread");
            return delegate.matches(rawPassword, encodedPassword);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BCrypt matches interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("BCrypt matches failed", e);
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
