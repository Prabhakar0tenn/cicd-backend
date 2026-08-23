package com.selfhealing.ai;

import com.selfhealing.domain.FailureContext;
import com.selfhealing.domain.PatchProposal;

/**
 * Common abstraction for AI reasoning engines (Gemini, Ollama, etc.).
 * Allows swapping AI providers without touching the core healing orchestrator.
 */
public interface AiProvider {

    /**
     * Analyzes the CI failure and generates a targeted patch proposal.
     *
     * @param context Complete failure diagnostic context
     * @return Structured PatchProposal containing root cause and file changes
     */
    PatchProposal generatePatch(FailureContext context);
}
