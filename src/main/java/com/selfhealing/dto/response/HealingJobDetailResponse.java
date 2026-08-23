package com.selfhealing.dto.response;

import com.selfhealing.model.HealingAttempt;
import com.selfhealing.model.HealingJob;
import com.selfhealing.model.PullRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealingJobDetailResponse {

    private HealingJobResponse job;
    @Builder.Default
    private List<HealingAttempt> attempts = new ArrayList<>();
    private PullRequest pullRequest;

    public static HealingJobDetailResponse of(HealingJob job, List<HealingAttempt> attempts, PullRequest pr) {
        return HealingJobDetailResponse.builder()
                .job(HealingJobResponse.from(job))
                .attempts(attempts != null ? attempts : new ArrayList<>())
                .pullRequest(pr)
                .build();
    }
}
