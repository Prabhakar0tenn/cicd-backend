package com.selfhealing.service;

import com.selfhealing.dto.request.FailureCallbackRequest;
import com.selfhealing.dto.response.HealingJobDetailResponse;
import com.selfhealing.dto.response.HealingJobResponse;
import com.selfhealing.enums.HealingStatus;
import com.selfhealing.exception.ResourceNotFoundException;
import com.selfhealing.model.BranchConfig;
import com.selfhealing.model.HealingAttempt;
import com.selfhealing.model.HealingJob;
import com.selfhealing.model.MonitoredRepo;
import com.selfhealing.model.PullRequest;
import com.selfhealing.repository.HealingAttemptRepository;
import com.selfhealing.repository.HealingJobRepository;
import com.selfhealing.repository.MonitoredRepoRepository;
import com.selfhealing.repository.PullRequestRepository;
import com.selfhealing.security.CallbackAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * High-level service for handling failure webhooks and retrieving healing job data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealingService {

    private final HealingJobRepository jobRepository;
    private final HealingAttemptRepository attemptRepository;
    private final PullRequestRepository prRepository;
    private final MonitoredRepoRepository repoRepository;
    private final CallbackAuthService callbackAuthService;
    private final HealingOrchestrator healingOrchestrator;

    /**
     * Handles incoming CI failure callback from GitHub Actions.
     *
     * @param request Webhook payload from GitHub Actions
     * @param secret Shared secret passed in X-Healer-Secret header
     * @return Created HealingJob or null if ignored
     */
    public HealingJob handleFailureWebhook(FailureCallbackRequest request, String secret) {
        // 1. Authenticate webhook
        if (!callbackAuthService.isValidCallbackSecret(secret)) {
            log.warn("Unauthorized webhook attempt for repo {}/{}", request.getOwner(), request.getRepository());
            throw new AccessDeniedException("Invalid X-Healer-Secret provided");
        }

        // 2. Anti-Loop Protection: Ignore failures on healing branches
        if (request.getBranch().startsWith("ai-healing/")) {
            log.info("Ignoring failure notification on healing branch: {}", request.getBranch());
            return null;
        }

        // 3. Find monitored repo configuration
        MonitoredRepo repo = repoRepository.findAll().stream()
                .filter(r -> r.getOwner().equalsIgnoreCase(request.getOwner()) &&
                             r.getName().equalsIgnoreCase(request.getRepository()))
                .findFirst()
                .orElse(null);

        if (repo == null) {
            log.warn("Received failure callback for unmonitored repo: {}/{}", request.getOwner(), request.getRepository());
            return null;
        }

        // 4. Verify autoHeal is enabled for repo and this specific branch
        if (!repo.isAutoHealEnabled()) {
            log.info("Auto-healing disabled for repo {}/{}", repo.getOwner(), repo.getName());
            return null;
        }

        BranchConfig branchConfig = repo.getBranches().stream()
                .filter(b -> b.getName().equals(request.getBranch()))
                .findFirst()
                .orElse(null);

        if (branchConfig != null && !branchConfig.isHealingEnabled()) {
            log.info("Healing disabled for branch {} in repo {}/{}", request.getBranch(), repo.getOwner(), repo.getName());
            return null;
        }

        int maxAttempts = branchConfig != null ? branchConfig.getMaxAttempts() : 3;

        // 5. Create new HealingJob in DETECTED status
        HealingJob job = HealingJob.builder()
                .repositoryId(repo.getId())
                .userId(repo.getUserId())
                .owner(repo.getOwner())
                .repoName(repo.getName())
                .branch(request.getBranch())
                .failedCommitSha(request.getCommitSha())
                .workflowRunId(request.getWorkflowRunId())
                .status(HealingStatus.DETECTED)
                .maxAttempts(maxAttempts)
                .build();

        HealingJob savedJob = jobRepository.save(job);
        log.info("HealingJob created: {} for {}/{} (branch: {})", savedJob.getId(), repo.getOwner(), repo.getName(), request.getBranch());

        // 6. Trigger async healing pipeline
        healingOrchestrator.startHealing(savedJob.getId());

        return savedJob;
    }

    /**
     * Lists paginated healing jobs for an authenticated user.
     */
    public Page<HealingJobResponse> getJobsForUser(String userId, Pageable pageable) {
        return jobRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(HealingJobResponse::from);
    }

    /**
     * Retrieves full detail of a single healing job with attempt history and PR data.
     */
    public HealingJobDetailResponse getJobDetail(String userId, String jobId) {
        HealingJob job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("HealingJob", jobId));

        List<HealingAttempt> attempts = attemptRepository.findByHealingJobIdOrderByAttemptNumberAsc(jobId);
        PullRequest pr = prRepository.findByHealingJobId(jobId).orElse(null);

        return HealingJobDetailResponse.of(job, attempts, pr);
    }

    /**
     * Manually triggers a retry for a failed or aborted healing job.
     */
    public HealingJobResponse retryJob(String userId, String jobId) {
        HealingJob job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("HealingJob", jobId));

        job.setStatus(HealingStatus.DETECTED);
        job.setAttemptCount(0);
        job.setErrorMessage(null);
        HealingJob updatedJob = jobRepository.save(job);

        healingOrchestrator.startHealing(jobId);
        return HealingJobResponse.from(updatedJob);
    }
}
