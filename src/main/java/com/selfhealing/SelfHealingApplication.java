package com.selfhealing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the Self-Healing CI/CD Platform backend.
 *
 * @EnableAsync enables Spring's @Async annotation so the HealingOrchestrator
 * can process CI failures in background threads without blocking HTTP requests.
 */
@SpringBootApplication
@EnableAsync
public class SelfHealingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SelfHealingApplication.class, args);
    }
}
