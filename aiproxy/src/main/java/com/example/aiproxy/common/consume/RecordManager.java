package com.example.aiproxy.common.consume;

import com.example.aiproxy.model.ChannelCache; // Placeholder
import com.example.aiproxy.model.GroupCache;   // Placeholder
import com.example.aiproxy.model.ModelLogHandler; // Placeholder for where BatchRecordLogs would reside
import com.example.aiproxy.model.Price;
import com.example.aiproxy.model.RequestDetail;
import com.example.aiproxy.model.TokenCache;    // Placeholder
import com.example.aiproxy.model.Usage;
import com.example.aiproxy.relay.meta.Meta;

import java.time.Instant;
import java.util.Map;
import java.util.logging.Logger;

public class RecordManager {
    private static final Logger LOGGER = Logger.getLogger(RecordManager.class.getName());

    /**
     * Records consumption details.
     * This method is a wrapper that passes through to a more specific logging/database handler.
     */
    public static void recordConsume(
            Meta meta,
            int httpStatusCode,
            Instant firstByteAt,
            Usage usage,
            Price modelPrice,
            String content, // Content from the original Consume function, purpose might be specific logging
            String ipAddress,
            RequestDetail requestDetail,
            double amount, // The final consumed amount
            int retryTimes,
            boolean downstreamResultSuccess,
            String user,
            Map<String, String> metadata // Additional metadata for logging
    ) throws Exception { // Assuming BatchRecordLogs might throw an exception

        // Basic null checks for critical parameters
        if (meta == null) {
            LOGGER.severe("Meta object is null in recordConsume. Cannot record log.");
            // Or throw new IllegalArgumentException("Meta cannot be null for recording consumption.");
            return;
        }
        // Ensure nested objects in meta that are accessed are also checked or handled by BatchRecordLogs
        String groupID = (meta.group != null) ? meta.group.getId() : null;
        String channelID = (meta.channel != null) ? meta.channel.id : null;
        String tokenID = (meta.token != null) ? meta.token.id : null;
        String tokenName = (meta.token != null) ? meta.token.name : null;


        // The actual logging/database interaction is delegated.
        // We assume a class like ModelLogHandler in the model package will have this static method.
        // This is a direct translation of the Go call structure.
        ModelLogHandler.batchRecordLogs(
                meta.requestID,
                meta.requestAt,
                meta.retryAt,       // Added to Meta.java
                firstByteAt,
                groupID,
                httpStatusCode,
                channelID,          // Added to Meta.java (via ChannelCache)
                meta.originModel,
                tokenID,
                tokenName,
                meta.endpoint,      // Added to Meta.java
                content,
                meta.mode,          // Added to Meta.java
                ipAddress,
                retryTimes,
                requestDetail,
                downstreamResultSuccess,
                usage,
                modelPrice,
                amount,
                user,
                metadata
        );
    }

    private RecordManager() {
        // Private constructor for utility class
    }
}
