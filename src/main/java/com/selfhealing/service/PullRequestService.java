package com.selfhealing.service;

import com.selfhealing.domain.FileChange;
import com.selfhealing.domain.PatchProposal;
import com.selfhealing.enums.HealingStatus;
import com.selfhealing.github.GithubClient;
import com.selfhealing.model.HealingJob;
import com.selfhealing.model.PullRequest;
import com.selfhealing.repository.HealingJobRepository;
import com.selfhealing.repository.PullRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Creates and tracks Pull Requests on GitHub after a patch passes CI validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PullRequestService {

    private final GithubClient githubClient;
    private final PullRequestRepository prRepository;
    private final HealingJobRepository jobRepository;

    /**
     * Creates a Pull Request on GitHub and records it in MongoDB.
     *
     * @param job The active healing job
     * @param proposal The successful patch proposal
     * @param attemptNumber The attempt number that succeeded
     * @param pat Decrypted GitHub PAT
     * @return Created PullRequest document
     */
    public PullRequest createPullRequest(
            HealingJob job,
            PatchProposal proposal,
            int attemptNumber,
            String pat) {

        String title = String.format("🤖 fix: resolve CI failure in %s (attempt #%d)", job.getBranch(), attemptNumber);
        String body = buildPrDescription(job, proposal, attemptNumber);

        log.info("Creating PR for {}/{} (head: {}, base: {})",
                job.getOwner(), job.getRepoName(), job.getHealingBranch(), job.getBranch());

        Map<String, Object> prResponse = githubClient.createPullRequest(
                job.getOwner(),
                job.getRepoName(),
                title,
                body,
                job.getHealingBranch(),
                job.getBranch(),
                pat
        );

        int prNumber = ((Number) prResponse.get("number")).intValue();
        String prUrl = (String) prResponse.get("html_url");

        PullRequest pr = PullRequest.builder()
                .healingJobId(job.getId())
                .repositoryId(job.getRepositoryId())
                .prNumber(prNumber)
                .prUrl(prUrl)
                .headBranch(job.getHealingBranch())
                .baseBranch(job.getBranch())
                .title(title)
                .status("OPEN")
                .build();

        PullRequest savedPr = prRepository.save(pr);

        // Update HealingJob with PR metadata
        job.setPrNumber(prNumber);
        job.setPrUrl(prUrl);
        job.setStatus(HealingStatus.PR_CREATED);
        jobRepository.save(job);

        log.info("PR #{} created successfully: {}", prNumber, prUrl);
        return savedPr;
    }

    /**
     * Constructs rich Markdown documentation for the pull request.
     */
    private String buildPrDescription(HealingJob job, PatchProposal proposal, int attemptNumber) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 🤖 Autonomous CI/CD Self-Healing Fix\n\n");

        sb.append("### 🔍 Root Cause Diagnosis\n");
        sb.append(proposal.getRootCause() != null ? proposal.getRootCause() : "Automatic patch generated for CI failure.").append("\n\n");

        if (proposal.getReasoning() != null && !proposal.getReasoning().isBlank()) {
            sb.append("### 💡 Fix Explanation\n");
            sb.append(proposal.getReasoning()).append("\n\n");
        }

        sb.append("### 🛠️ Modified Files\n");
        if (proposal.getChanges() != null) {
            for (FileChange change : proposal.getChanges()) {
                sb.append("- `").append(change.getFilePath()).append("` (")
                  .append(change.getOperation() != null ? change.getOperation() : "REPLACE").append(")\n");
            }
        }
        sb.append("\n");

        sb.append("### 🧪 Validation Details\n");
        sb.append("- **Branch Tested:** `").append(job.getHealingBranch()).append("`\n");
        sb.append("- **Target Branch:** `").append(job.getBranch()).append("`\n");
        sb.append("- **Original Failed Commit:** `").append(job.getFailedCommitSha()).append("`\n");
        sb.append("- **Attempt:** ").append(attemptNumber).append(" of ").append(job.getMaxAttempts()).append("\n");
        sb.append("- **AI Confidence:** ").append((int) (proposal.getConfidence() * 100)).append("%\n\n");

        sb.append("---\n");
        sb.append("*Generated autonomously by [Self-Healing CI/CD Platform](https://github.com).*");

        return sb.toString();
    }
}
