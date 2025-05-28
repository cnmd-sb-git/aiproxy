package com.example.aiproxy.common.consume;

import com.example.aiproxy.common.balance.PostGroupConsumer;
import com.example.aiproxy.model.GroupCache;
import com.example.aiproxy.model.Price;
import com.example.aiproxy.model.RequestDetail;
import com.example.aiproxy.model.TokenCache;
import com.example.aiproxy.model.Usage;
import com.example.aiproxy.relay.meta.Meta; // Assuming Meta is in relay.meta

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

// Assuming model.CreateConsumeError and notify.ErrorThrottle will be handled/logged.
// For now, direct logging will be used for errors that were previously sent to notify.
// Placeholder for RecordManager.recordConsume which will be in RecordManager.java
// import com.example.aiproxy.common.consume.RecordManager;


public class ConsumeManager {

    private static final Logger LOGGER = Logger.getLogger(ConsumeManager.class.getName());

    // Using a cached thread pool for asynchronous consumption tasks.
    // Consider a fixed-size pool or a more configurable one for production.
    private static final ExecutorService consumeExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName("consume-worker-" + thread.threadId());
        return thread;
    });

    /**
     * Waits for all submitted asynchronous consumption tasks to complete.
     * This is a best-effort wait, typically used during graceful shutdown.
     * It will wait for a specified timeout.
     */
    public static void awaitTermination(long timeout, TimeUnit unit) {
        LOGGER.info("Attempting to shut down consume executor and wait for tasks to complete.");
        consumeExecutor.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!consumeExecutor.awaitTermination(timeout, unit)) {
                LOGGER.warning("Consume executor did not terminate in the specified time.");
                consumeExecutor.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!consumeExecutor.awaitTermination(timeout, unit)) {
                    LOGGER.severe("Consume executor did not terminate even after shutdownNow.");
                }
            }
        } catch (InterruptedException ie) {
            LOGGER.log(Level.WARNING, "Awaiting consume executor termination was interrupted.", ie);
            consumeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Consume executor shut down.");
    }
    
    /**
     * Asynchronously processes consumption.
     */
    public static void asyncConsume(
            PostGroupConsumer postGroupConsumer,
            int httpStatusCode,
            Instant firstByteAt,
            Meta meta,
            Usage usage,
            Price modelPrice,
            String content, // This seems to be a placeholder for request/response content for logging
            String ipAddress,
            int retryTimes,
            RequestDetail requestDetail, // Contains request/response body for logging
            boolean downstreamResultSuccess,
            String user,
            Map<String, String> metadata // Additional metadata for logging
    ) {
        consumeExecutor.submit(() -> {
            try {
                consume(
                        postGroupConsumer,
                        firstByteAt,
                        httpStatusCode,
                        meta,
                        usage,
                        modelPrice,
                        content,
                        ipAddress,
                        retryTimes,
                        requestDetail,
                        downstreamResultSuccess,
                        user,
                        metadata);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during asynchronous consumption for request ID " + (meta != null ? meta.requestID : "unknown"), e);
                // Optionally, notify or handle this error more specifically
            }
        });
    }

    /**
     * Synchronously processes consumption: calculates amount, updates balance, and records the transaction.
     */
    public static void consume(
            PostGroupConsumer postGroupConsumer,
            Instant firstByteAt,
            int httpStatusCode,
            Meta meta,
            Usage usage,
            Price modelPrice,
            String content,
            String ipAddress,
            int retryTimes,
            RequestDetail requestDetail,
            boolean downstreamResultSuccess,
            String user,
            Map<String, String> metadata) {

        if (meta == null) {
            LOGGER.severe("Meta object is null, cannot process consumption.");
            return;
        }
        if (usage == null) {
            LOGGER.severe("Usage object is null, cannot process consumption for request ID " + meta.requestID);
            return;
        }
         if (modelPrice == null) {
            LOGGER.severe("ModelPrice object is null, cannot process consumption for request ID " + meta.requestID);
            return;
        }


        double calculatedAmount = calculateAmount(httpStatusCode, usage, modelPrice);
        double finalConsumedAmount = processGroupConsumption(calculatedAmount, postGroupConsumer, meta);

        try {
            // This will be a static call to a method in a class like RecordManager (from record.go)
            RecordManager.recordConsume( // Assuming RecordManager class and its static method
                    meta,
                    httpStatusCode,
                    firstByteAt,
                    usage,
                    modelPrice,
                    content, // This might be part of requestDetail or a separate field
                    ipAddress,
                    requestDetail,
                    finalConsumedAmount,
                    retryTimes,
                    downstreamResultSuccess,
                    user,
                    metadata);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error recording consumption for request ID " + meta.requestID + ": " + e.getMessage(), e);
            // In Go, this used: notify.ErrorThrottle("recordConsume", time.Minute, "record consume failed", err.Error())
            // Placeholder for notification, logging serves a similar purpose for now.
            LOGGER.warning("Notification needed: Record consume failed for request ID " + meta.requestID);
        }
    }

    private static double processGroupConsumption(
            double calculatedAmount,
            PostGroupConsumer postGroupConsumer,
            Meta meta) {
        
        if (calculatedAmount > 0 && postGroupConsumer != null) {
            try {
                // meta.Token.Name is used in Go. Ensure TokenCache has a name field.
                String tokenName = (meta.token != null) ? meta.token.name : "unknown_token";
                double actualConsumedAmount = postGroupConsumer.postGroupConsume(tokenName, calculatedAmount);
                LOGGER.fine("Group consumption processed for request ID " + meta.requestID +
                            ". Calculated: " + calculatedAmount + ", Actual Consumed: " + actualConsumedAmount);
                return actualConsumedAmount;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during post group consumption for request ID " + meta.requestID + ": " + e.getMessage(), e);
                // Log error for balance deduction failure (model.CreateConsumeError in Go)
                // This would involve saving an error record to a database or logging system.
                // For now, just log it.
                if (meta.group != null && meta.token != null) {
                     LOGGER.severe(String.format(
                        "Failed to create consume error record for requestID %s, group %s, token %s, model %s, amount %.6f. Error: %s",
                        meta.requestID, meta.group.getId(), meta.token.name, meta.originModel, calculatedAmount, e.getMessage()
                    ));
                }
                return calculatedAmount; // Return original amount if consumption failed, as per Go logic
            }
        }
        return calculatedAmount;
    }

    public static double calculateAmount(
            int httpStatusCode,
            Usage usage,
            Price modelPrice) {

        if (modelPrice.perRequestPrice != 0) {
            if (httpStatusCode != HttpURLConnection.HTTP_OK) { // java.net.HttpURLConnection for status codes
                return 0.0;
            }
            return modelPrice.perRequestPrice;
        }

        long inputTokens = usage.inputTokens;
        if (modelPrice.imageInputPrice > 0) {
            inputTokens -= usage.imageInputTokens;
        }
        if (modelPrice.cachedPrice > 0) {
            inputTokens -= usage.cachedTokens;
        }
        if (modelPrice.cacheCreationPrice > 0) {
            inputTokens -= usage.cacheCreationTokens;
        }

        long outputTokens = usage.outputTokens;
        double outputPriceValue = modelPrice.outputPrice;
        long outputPriceUnitValue = modelPrice.getOutputPriceUnit();

        if (usage.reasoningTokens != 0 && modelPrice.thinkingModeOutputPrice != 0) {
            outputPriceValue = modelPrice.thinkingModeOutputPrice;
            if (modelPrice.getThinkingModeOutputPriceUnit() != 0) { // Check specific unit for thinking mode
                outputPriceUnitValue = modelPrice.getThinkingModeOutputPriceUnit();
            }
        }
        
        // Using BigDecimal for precision, matching shopspring/decimal
        BigDecimal inputAmount = BigDecimal.valueOf(inputTokens)
                .multiply(BigDecimal.valueOf(modelPrice.inputPrice))
                .divide(BigDecimal.valueOf(modelPrice.getInputPriceUnit()), 10, RoundingMode.HALF_UP);

        BigDecimal imageInputAmount = BigDecimal.valueOf(usage.imageInputTokens)
                .multiply(BigDecimal.valueOf(modelPrice.imageInputPrice))
                .divide(BigDecimal.valueOf(modelPrice.getImageInputPriceUnit()), 10, RoundingMode.HALF_UP);
        
        BigDecimal cachedAmount = BigDecimal.valueOf(usage.cachedTokens)
                .multiply(BigDecimal.valueOf(modelPrice.cachedPrice))
                .divide(BigDecimal.valueOf(modelPrice.getCachedPriceUnit()), 10, RoundingMode.HALF_UP);

        BigDecimal cacheCreationAmount = BigDecimal.valueOf(usage.cacheCreationTokens)
                .multiply(BigDecimal.valueOf(modelPrice.cacheCreationPrice))
                .divide(BigDecimal.valueOf(modelPrice.getCacheCreationPriceUnit()), 10, RoundingMode.HALF_UP);
        
        BigDecimal webSearchAmount = BigDecimal.valueOf(usage.webSearchCount)
                .multiply(BigDecimal.valueOf(modelPrice.webSearchPrice))
                .divide(BigDecimal.valueOf(modelPrice.getWebSearchPriceUnit()), 10, RoundingMode.HALF_UP);

        BigDecimal outputAmount = BigDecimal.valueOf(outputTokens)
                .multiply(BigDecimal.valueOf(outputPriceValue))
                .divide(BigDecimal.valueOf(outputPriceUnitValue), 10, RoundingMode.HALF_UP);
        
        BigDecimal totalAmount = inputAmount
                .add(imageInputAmount)
                .add(cachedAmount)
                .add(cacheCreationAmount)
                .add(webSearchAmount)
                .add(outputAmount);
        
        return totalAmount.doubleValue(); // Corresponds to InexactFloat64()
    }

    private ConsumeManager() {
        // Private constructor for utility class
    }
}
