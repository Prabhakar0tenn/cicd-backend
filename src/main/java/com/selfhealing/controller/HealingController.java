package com.selfhealing.controller;

import com.selfhealing.dto.request.FailureCallbackRequest;
import com.selfhealing.dto.response.ApiResponse;
import com.selfhealing.dto.response.HealingJobDetailResponse;
import com.selfhealing.dto.response.HealingJobResponse;
import com.selfhealing.model.HealingJob;
import com.selfhealing.service.HealingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for CI failure callbacks and healing job inspection.
 */
@RestController
@RequestMapping("/api/healing")
@RequiredArgsConstructor
public class HealingController {

    private final HealingService healingService;

    /**
     * Public endpoint called by GitHub Actions on CI workflow failure.
     * Authenticated via X-Healer-Secret header.
     */
    @PostMapping("/failure")
    public ResponseEntity<ApiResponse<HealingJobResponse>> onCiFailure(
            @RequestHeader(value = "X-Healer-Secret", required = false) String secret,
            @Valid @RequestBody FailureCallbackRequest request) {

        HealingJob job = healingService.handleFailureWebhook(request, secret);

        if (job == null) {
            return ResponseEntity.ok(ApiResponse.success("Notification ignored or not monitored", null));
        }

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Failure callback received. Autonomous healing started.", HealingJobResponse.from(job)));
    }

    /**
     * Lists paginated healing jobs for the authenticated user.
     */
    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<Page<HealingJobResponse>>> getJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        Page<HealingJobResponse> jobs = healingService.getJobsForUser(userId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success("Healing jobs retrieved", jobs));
    }

    /**
     * Gets detailed information for a specific healing job, including attempts and PR link.
     */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<HealingJobDetailResponse>> getJobDetail(
            @PathVariable String id,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        HealingJobDetailResponse detail = healingService.getJobDetail(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Healing job detail retrieved", detail));
    }

    /**
     * Manually triggers a retry for a failed healing job.
     */
    @PostMapping("/jobs/{id}/retry")
    public ResponseEntity<ApiResponse<HealingJobResponse>> retryJob(
            @PathVariable String id,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        HealingJobResponse retryResult = healingService.retryJob(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Healing job retry initiated", retryResult));
    }
}
