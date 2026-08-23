package com.selfhealing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test — verifies the Spring context loads successfully with all beans.
 *
 * @SpringBootTest starts the full application context.
 * If any bean fails to initialize (wrong config, missing dependency, etc.),
 * this test will fail — catching those errors early before deployment.
 *
 * Environment variables are set via @TestPropertySource so this test can
 * run in CI without real secrets (uses safe placeholder values).
 */
@SpringBootTest
@TestPropertySource(properties = {
    "MONGODB_URI=mongodb+srv://prabhakar0tenn_db_user:peter0lily07@cluster0.pd6jgqi.mongodb.net/?appName=Cluster0",
    "GEMINI_API_KEY=AIzaSyCEiWjuKzKnbg8f1gZWcoN4JDKPhNkuaZU",
    "ENCRYPTION_KEY=Y7JlZmoRa4NS+X8bJEFOoh793OAcN4izfk1fFwA7Qp8=",
    "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters",
    "HEALER_CALLBACK_SECRET=test-callback-secret"
})
class SelfHealingApplicationTests {

    /**
     * Verifies the Spring application context loads without errors.
     * This is the most important baseline test.
     */
    @Test
    void contextLoads() {
        // If this method runs, the context started successfully.
        // No assertions needed — a startup failure causes the test to fail automatically.
    }
}
