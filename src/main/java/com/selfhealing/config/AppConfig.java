package com.selfhealing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * General application configuration.
 *
 * Responsibilities:
 * - Defines the thread pool used by @Async healing workers
 * - Provides a RestTemplate bean for making HTTP calls (GitHub API, Gemini API)
 *
 * Why a dedicated thread pool?
 * The healing process (fetch logs → call AI → push branch → wait for CI)
 * can take several minutes. We run it asynchronously so the HTTP response
 * to the GitHub Actions callback returns immediately (202 Accepted).
 */
@Configuration
public class AppConfig {

    @Value("${app.async.core-pool-size}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity}")
    private int queueCapacity;

    /**
     * Thread pool for async healing jobs.
     * Named "healingTaskExecutor" so @Async("healingTaskExecutor") picks it up.
     */
    @Bean(name = "healingTaskExecutor")
    public Executor healingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("healing-worker-");
        executor.initialize();
        return executor;
    }

    /**
     * General-purpose HTTP client for calling external APIs.
     * GitHub Client and Gemini Provider both inject this bean.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
