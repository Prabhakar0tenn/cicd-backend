package com.selfhealing.ai;

import com.selfhealing.domain.FailureContext;
import com.selfhealing.domain.PatchProposal;
import com.selfhealing.exception.AiProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ollama local AI provider stub (Phase 9 capability).
 */
@Service
@Slf4j
public class OllamaAiProvider implements AiProvider {

    @Override
    public PatchProposal generatePatch(FailureContext context) {
        log.warn("Ollama AI provider requested, but local instance is not enabled in current environment.");
        throw new AiProviderException("Ollama provider is configured for Phase 9 local deployments.");
    }
}
