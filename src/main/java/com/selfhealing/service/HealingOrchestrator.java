package com.selfhealing.service;

import com.selfhealing.ai.AiProvider;
import com.selfhealing.domain.FailureContext;
import com.selfhealing.domain.FailureInfo;
import com.selfhealing.domain.PatchProposal;
import com.selfhealing.enums.HealingStatus;
import com.selfhealing.enums.ValidationResult;
import com.selfhealing.github.GithubBranchService;
import com.selfhealing.github.GithubWorkflowService;
import com.selfhealing.model.HealingAttempt;
import com.selfhealing.model.HealingJob;
import com.selfhealing.repository.HealingAttemptRepository;
import com.selfhealing.repository.HealingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Central Orchestrator & State Machine for the Self-Healing CI/CD Platform.
 * Executes the complete autonomous healing pipeline asynchronously.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealingOrchestrator {

    private final HealingJobRepository jobRepository;
    private final HealingAttemptRepository attemptRepository;
    private final CredentialService credentialService;
    private final GithubWorkflowService workflowService;
    private final FailureAnalysisService failureAnalysisService;
    private final ContextBuilderService contextBuilderService;
    private final AiProvider aiProvider;
    private final PatchValidationService patchValidationService;
    private final GithubBranchService branchService;
    private final ValidationService validationService;
    private final PullRequestService pullRequestService;

    /**
     * Entry point for asynchronous healing workflow.
     *
     * @param jobId MongoDB ID of the newly created HealingJob
     */
    @Async("healingTaskExecutor")
    public void startHealing(String jobId) {
        HealingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("HealingJob {} not found. Aborting orchestrator.", jobId);
            return;
        }

        log.info("▶ Starting Autonomous Healing Pipeline for Job {} on {}/{} (branch: {})",
                job.getId(), job.getOwner(), job.getRepoName(), job.getBranch());

        String pat = credentialService.getDecryptedToken(job.getUserId());
        List<String> previousAttemptFeedback = new ArrayList<>();

        try {
            // STEP 1: Branch Freshness & Anti-Loop Check
            String currentHeadSha = branchService.getBranchHeadSha(job.getOwner(), job.getRepoName(), job.getBranch(), pat);
            if (currentHeadSha != null && !currentHeadSha.equals(job.getFailedCommitSha())) {
                log.warn("Branch {} SHA changed from {} to {}. Aborting job to prevent stale fix.",
                        job.getBranch(), job.getFailedCommitSha(), currentHeadSha);
                updateJobStatus(job, HealingStatus.ABORTED, "Branch moved while healing was queued.");
                return;
            }

            // STEP 2: Fetch Logs & Analyze Failure
            updateJobStatus(job, HealingStatus.ANALYZING, null);
            Map<String, String> failedJobInfo = workflowService.getFailedJobInfo(
                    job.getOwner(), job.getRepoName(), job.getWorkflowRunId(), pat);
            String failedStep = failedJobInfo.getOrDefault("failedStep", "build");

            String rawLogs = workflowService.downloadWorkflowLogs(
                    job.getOwner(), job.getRepoName(), job.getWorkflowRunId(), pat);

            FailureInfo failureInfo = failureAnalysisService.parseFailure(rawLogs, failedStep);
            job.setFailureType(failureInfo.getFailureType());
            job.setFailureSummary(failureInfo.getErrorMessage());
            jobRepository.save(job);

            log.info("Analysis complete. Failure Category: {}, Error: {}",
                    failureInfo.getFailureType(), failureInfo.getErrorMessage());

            // ITERATIVE HEALING LOOP (Up to maxAttempts)
            for (int attempt = 1; attempt <= job.getMaxAttempts(); attempt++) {
                job.setAttemptCount(attempt);
                log.info("🔄 Initiating Healing Attempt #{} / {}", attempt, job.getMaxAttempts());

                // STEP 3: Assemble Failure Context
                updateJobStatus(job, HealingStatus.CONTEXT_BUILDING, null);
                FailureContext context = contextBuilderService.buildContext(
                        job.getOwner(),
                        job.getRepoName(),
                        job.getBranch(),
                        job.getFailedCommitSha(),
                        job.getWorkflowRunId(),
                        failureInfo,
                        previousAttemptFeedback,
                        pat
                );

                // STEP 4: AI Reasoning with Gemini
                updateJobStatus(job, HealingStatus.AI_ANALYSIS, null);
                PatchProposal proposal = aiProvider.generatePatch(context);
                log.info("AI Patch received. Root cause: {}", proposal.getRootCause());

                // STEP 5: Patch Safety Validation
                updateJobStatus(job, HealingStatus.PATCH_GENERATED, null);
                Map<String, String> patchedFiles = patchValidationService.validateAndApplyChanges(proposal, context);

                // STEP 6: Create Healing Branch & Commit
                String healingBranch = String.format("ai-healing/job-%s-attempt-%d", job.getId().substring(0, Math.min(8, job.getId().length())), attempt);
                job.setHealingBranch(healingBranch);
                updateJobStatus(job, HealingStatus.VALIDATING, null);

                branchService.createHealingBranch(
                        job.getOwner(), job.getRepoName(), healingBranch, job.getFailedCommitSha(), pat);

                String commitMsg = String.format("🤖 fix: resolve CI failure in %s (attempt #%d)", job.getBranch(), attempt);
                branchService.commitPatchedFiles(
                        job.getOwner(), job.getRepoName(), healingBranch, patchedFiles, commitMsg, pat);

                // Record attempt in database
                HealingAttempt attemptRecord = HealingAttempt.builder()
                        .healingJobId(job.getId())
                        .attemptNumber(attempt)
                        .diagnosis(proposal.getRootCause())
                        .confidence(proposal.getConfidence())
                        .reasoning(proposal.getReasoning())
                        .branchName(healingBranch)
                        .build();

                // STEP 7: Validate through GitHub Actions
                ValidationService.ValidationOutcome outcome = validationService.waitForValidation(
                        job.getOwner(), job.getRepoName(), healingBranch, pat);

                attemptRecord.setValidationResult(outcome.result());
                attemptRecord.setValidationRunId(outcome.runId());
                attemptRecord.setValidationErrorLogs(outcome.errorLogs());
                attemptRepository.save(attemptRecord);

                if (outcome.result() == ValidationResult.PASS) {
                    log.info("✅ Validation PASSED on attempt #{}", attempt);
                    updateJobStatus(job, HealingStatus.SUCCESS, null);

                    // STEP 8: Create Pull Request
                    pullRequestService.createPullRequest(job, proposal, attempt, pat);
                    log.info("🎉 Autonomous healing workflow completed successfully for job {}", job.getId());
                    return;
                } else {
                    log.warn("❌ Validation failed on attempt #{}. Result: {}", attempt, outcome.result());
                    if (attempt < job.getMaxAttempts()) {
                        updateJobStatus(job, HealingStatus.RETRYING, "Validation failed. Retrying with enriched context.");
                        previousAttemptFeedback.add(String.format("Attempt #%d on branch %s failed: %s",
                                attempt, healingBranch, outcome.errorLogs()));
                    }
                }
            }

            // All attempts exhausted
            updateJobStatus(job, HealingStatus.FAILED, "Exhausted all " + job.getMaxAttempts() + " healing attempts.");
            log.error("❌ Healing job {} FAILED after all attempts.", job.getId());

        } catch (Exception e) {
            log.error("💥 Unhandled exception in healing pipeline for job {}: {}", job.getId(), e.getMessage(), e);
            updateJobStatus(job, HealingStatus.FAILED, "Internal pipeline error: " + e.getMessage());
        }
    }

    private void updateJobStatus(HealingJob job, HealingStatus status, String errorMessage) {
        job.setStatus(status);
        if (errorMessage != null) {
            job.setErrorMessage(errorMessage);
        }
        jobRepository.save(job);
    }
}
