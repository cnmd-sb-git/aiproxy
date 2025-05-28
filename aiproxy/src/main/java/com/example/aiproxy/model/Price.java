package com.example.aiproxy.model;

/**
 * Placeholder for model.Price.
 * Fields will be added based on the Go definition.
 */
public class Price {
    public double perRequestPrice;
    public double inputPrice;
    public double outputPrice;
    public double imageInputPrice;
    public double cachedPrice;
    public double cacheCreationPrice;
    public double webSearchPrice;
    public double thinkingModeOutputPrice; // Corresponds to ThinkingModeOutputPrice

    // Price units - assuming these are divisors (e.g., price per 1000 tokens)
    private long inputPriceUnit = 1;
    private long outputPriceUnit = 1;
    private long imageInputPriceUnit = 1;
    private long cachedPriceUnit = 1;
    private long cacheCreationPriceUnit = 1;
    private long webSearchPriceUnit = 1;
    private long thinkingModeOutputPriceUnit = 0; // 0 means use default outputPriceUnit

    public Price() {}

    // Getters that provide default unit if specific unit is 0 or not set
    public long getInputPriceUnit() {
        return inputPriceUnit == 0 ? 1 : inputPriceUnit;
    }
    public long getOutputPriceUnit() {
        return outputPriceUnit == 0 ? 1 : outputPriceUnit;
    }
    public long getImageInputPriceUnit() {
        return imageInputPriceUnit == 0 ? 1 : imageInputPriceUnit;
    }
    public long getCachedPriceUnit() {
        return cachedPriceUnit == 0 ? 1 : cachedPriceUnit;
    }
    public long getCacheCreationPriceUnit() {
        return cacheCreationPriceUnit == 0 ? 1 : cacheCreationPriceUnit;
    }
    public long getWebSearchPriceUnit() {
        return webSearchPriceUnit == 0 ? 1 : webSearchPriceUnit;
    }
     public long getThinkingModeOutputPriceUnit() {
        return thinkingModeOutputPriceUnit == 0 ? getOutputPriceUnit() : thinkingModeOutputPriceUnit;
    }

    // Setters
    public void setInputPriceUnit(long unit) { this.inputPriceUnit = unit; }
    public void setOutputPriceUnit(long unit) { this.outputPriceUnit = unit; }
    public void setImageInputPriceUnit(long unit) { this.imageInputPriceUnit = unit; }
    public void setCachedPriceUnit(long unit) { this.cachedPriceUnit = unit; }
    public void setCacheCreationPriceUnit(long unit) { this.cacheCreationPriceUnit = unit; }
    public void setWebSearchPriceUnit(long unit) { this.webSearchPriceUnit = unit; }
    public void setThinkingModeOutputPriceUnit(long unit) { this.thinkingModeOutputPriceUnit = unit; }

}
