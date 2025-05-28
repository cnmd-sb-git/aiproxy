package com.example.aiproxy.model;

/**
 * Placeholder for model.Usage.
 * Fields will be added based on the Go definition.
 */
public class Usage {
    public int inputTokens;
    public int outputTokens;
    public int imageInputTokens; // Corresponds to ImageInputTokens
    public int cachedTokens;
    public int cacheCreationTokens;
    public int webSearchCount;
    public int reasoningTokens; // Corresponds to ReasoningTokens

    public Usage() {}

    public Usage(int inputTokens, int outputTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }
    // Add other fields as necessary
}
