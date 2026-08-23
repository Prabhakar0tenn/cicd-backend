package com.selfhealing.service;

import com.selfhealing.domain.FailureContext;
import com.selfhealing.domain.FailureInfo;
import com.selfhealing.domain.FileChange;
import com.selfhealing.domain.PatchProposal;
import com.selfhealing.enums.FailureType;
import com.selfhealing.exception.PatchValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite validating core domain logic:
 * - AES-256-GCM Encryption / Decryption
 * - CI Log Parsing (Build vs Test)
 * - AI Patch Safety Validation & Application
 */
class CoreServicesTest {

    private EncryptionService encryptionService;
    private FailureAnalysisService failureAnalysisService;
    private PatchValidationService patchValidationService;

    @BeforeEach
    void setUp() {
        // Setup 32-byte AES key
        byte[] keyBytes = Base64.getDecoder().decode("Y7JlZmoRa4NS+X8bJEFOoh793OAcN4izfk1fFwA7Qp8=");
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

        encryptionService = new EncryptionService(secretKey);
        failureAnalysisService = new FailureAnalysisService();
        patchValidationService = new PatchValidationService();
    }

    @Test
    @DisplayName("EncryptionService: Encrypt and decrypt produces original plaintext")
    void testEncryptionDecryptionCycle() {
        String originalPat = "ghp_TestPersonalAccessToken1234567890abcdef";
        String encrypted = encryptionService.encrypt(originalPat);

        assertNotNull(encrypted);
        assertNotEquals(originalPat, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(originalPat, decrypted);
    }

    @Test
    @DisplayName("FailureAnalysisService: Correctly classifies Maven compilation failure")
    void testParseBuildFailure() {
        String logSample = """
                [INFO] --- compiler:3.13.0:compile (default-compile) @ my-app ---
                [INFO] Compiling 1 source file
                [ERROR] /home/runner/work/app/src/main/java/com/example/PaymentService.java:[42,15] cannot find symbol
                  symbol:   method process()
                  location: class com.example.Gateway
                [INFO] ------------------------------------------------------------------------
                [INFO] BUILD FAILURE
                """;

        FailureInfo info = failureAnalysisService.parseFailure(logSample, "Build and Compile");

        assertEquals(FailureType.BUILD, info.getFailureType());
        assertTrue(info.getErrorMessage().contains("cannot find symbol"));
        assertTrue(info.getErrorLocations().stream().anyMatch(loc -> loc.contains("PaymentService.java")));
    }

    @Test
    @DisplayName("FailureAnalysisService: Correctly classifies JUnit test failure")
    void testParseTestFailure() {
        String logSample = """
                [INFO] Running com.example.OrderTest
                [ERROR] Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.12 s <<< FAILURE! -- in com.example.OrderTest
                [ERROR] testCalculateDiscount  Time elapsed: 0.01 s  <<< FAILURE!
                org.opentest4j.AssertionFailedError: expected: <100> but was: <80>
                	at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:55)
                	at com.example.OrderTest.testCalculateDiscount(OrderTest.java:34)
                """;

        FailureInfo info = failureAnalysisService.parseFailure(logSample, "Run Unit Tests");

        assertEquals(FailureType.TEST, info.getFailureType());
        assertNotNull(info.getStackTrace());
        assertTrue(info.getStackTrace().contains("AssertionFailedError"));
    }

    @Test
    @DisplayName("PatchValidationService: Rejects path traversal attack")
    void testPatchSafetyPathTraversal() {
        PatchProposal proposal = PatchProposal.builder()
                .changes(List.of(FileChange.builder()
                        .filePath("../../etc/passwd")
                        .operation("REPLACE")
                        .oldCode("root")
                        .newCode("hacked")
                        .build()))
                .build();

        FailureContext context = FailureContext.builder().build();

        assertThrows(PatchValidationException.class, () ->
                patchValidationService.validateAndApplyChanges(proposal, context));
    }

    @Test
    @DisplayName("PatchValidationService: Successfully replaces exact matching code")
    void testPatchApplicationSuccess() {
        String originalCode = """
                public class Calculator {
                    public int add(int a, int b) {
                        return a - b; // Bug here
                    }
                }
                """;

        PatchProposal proposal = PatchProposal.builder()
                .changes(List.of(FileChange.builder()
                        .filePath("src/main/java/Calculator.java")
                        .operation("REPLACE")
                        .oldCode("return a - b; // Bug here")
                        .newCode("return a + b;")
                        .build()))
                .build();

        FailureContext context = FailureContext.builder()
                .sourceFiles(Map.of("src/main/java/Calculator.java", originalCode))
                .build();

        Map<String, String> patched = patchValidationService.validateAndApplyChanges(proposal, context);

        assertTrue(patched.containsKey("src/main/java/Calculator.java"));
        assertTrue(patched.get("src/main/java/Calculator.java").contains("return a + b;"));
        assertFalse(patched.get("src/main/java/Calculator.java").contains("return a - b;"));
    }
}
