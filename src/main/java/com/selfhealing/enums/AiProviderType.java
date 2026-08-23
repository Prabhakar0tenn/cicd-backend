package com.selfhealing.enums;

/**
 * Supported AI providers.
 * Matches the value expected in the app.ai.provider property.
 */
public enum AiProviderType {

    /** Google Gemini API — the default provider */
    GEMINI,

    /** Local Ollama instance — Phase 9, optional */
    OLLAMA
}
