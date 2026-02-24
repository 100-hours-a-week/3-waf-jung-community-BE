package com.ktb.community.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder + BCrypt 전용 Thread Pool 설정.
 *
 * <p>Virtual Thread 환경에서 BCrypt(CPU-bound)가 carrier thread를 pin하는 문제를 방지하기 위해
 * 전용 Platform Thread Pool(bcrypt-worker)에서 실행하고,
 * Virtual Thread는 {@code Future.get()}으로 carrier에서 unmount된다.</p>
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService bcryptExecutor() {
        int poolSize = Runtime.getRuntime().availableProcessors();
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "bcrypt-worker");
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(50),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder(ExecutorService bcryptExecutor) {
        return new OffloadingPasswordEncoder(new BCryptPasswordEncoder(10), bcryptExecutor);
    }
}
