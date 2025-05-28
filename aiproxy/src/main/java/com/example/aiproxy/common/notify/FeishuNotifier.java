package com.example.aiproxy.common.notify;

import com.example.aiproxy.common.config.AppConfig; // For AppConfig.getNotifyNote()
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notifier implementation for sending messages to Feishu (Lark) via webhooks.
 */
public class FeishuNotifier implements Notifier {

    private static final Logger LOGGER = Logger.getLogger(FeishuNotifier.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // A shared thread pool for sending notifications asynchronously.
    // Consider making this configurable or managed globally if many notifiers use it.
    private static final ExecutorService asyncNotifierExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName("feishu-notifier-worker-" + thread.threadId());
        thread.setDaemon(true); // Allow JVM to exit if only daemon threads are running
        return thread;
    });


    private final String webhookUrl;
    private final Notifier consoleFallbackNotifier = new StdNotifier(); // For local logging as per Go behavior

    public FeishuNotifier(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            throw new IllegalArgumentException("Feishu webhook URL cannot be null or empty.");
        }
        this.webhookUrl = webhookUrl;
        LOGGER.info("FeishuNotifier initialized with webhook URL.");
    }

    private String levelToFeishuColor(NotificationLevel level) {
        switch (level) {
            case INFO:  return "green";
            case WARN:  return "orange";
            case ERROR: return "red";
            default:    return "blue"; // A neutral default
        }
    }

    @Override
    public void notify(NotificationLevel level, String title, String message) {
        // Log to console first, similar to Go's stdNotifier.Notify behavior
        consoleFallbackNotifier.notify(level, title, message);

        // Asynchronously send to Feishu
        asyncNotifierExecutor.submit(() -> {
            try {
                postToFeishuV2(levelToFeishuColor(level), title, message, this.webhookUrl);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to send Feishu notification for title: " + title, e);
            }
        });
    }

    @Override
    public void notifyThrottle(NotificationLevel level, String key, Duration expiration, String title, String message) {
        // The key is already formatted by NotificationManager.formatThrottleKey
        if (!MemoryTryLock.tryLock(key, expiration)) {
            LOGGER.finer("Feishu notification throttled for key: " + key + ", title: " + title);
            return;
        }

        // Log to console first
        consoleFallbackNotifier.notify(level, title, message);

        // Asynchronously send to Feishu
        asyncNotifierExecutor.submit(() -> {
            try {
                postToFeishuV2(levelToFeishuColor(level), title, message, this.webhookUrl);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to send throttled Feishu notification for title: " + title, e);
            }
        });
    }

    // --- Feishu Message Payload POJOs (inner classes for encapsulation) ---

    private static class FeishuMessageV2 {
        @JsonProperty("msg_type")
        public String msgType = "interactive"; // Fixed as per Go code
        @JsonProperty("card")
        public FeishuCard card;
    }

    private static class FeishuCard {
        @JsonProperty("config")
        public FeishuCardConfig config;
        @JsonProperty("elements")
        public List<FeishuElement> elements;
        @JsonProperty("header")
        public FeishuHeader header;
    }

    private static class FeishuCardConfig {
        @JsonProperty("wide_screen_mode")
        public boolean wideScreenMode = true;
        @JsonProperty("enable_forward")
        public boolean enableForward = true;
    }

    private static class FeishuHeader {
        @JsonProperty("title")
        public FeishuTextContent title;
        @JsonProperty("template")
        public String template; // color: green, orange, red etc.
    }
    
    private static class FeishuTextContent {
        @JsonProperty("content")
        public String content;
        @JsonProperty("tag")
        public String tag = "plain_text"; // Default, can be "lark_md"

        public FeishuTextContent(String content, String tag) {
            this.content = content;
            this.tag = tag;
        }
         public FeishuTextContent(String content) {
            this.content = content;
            // Default tag is plain_text
        }
    }

    private static class FeishuElement {
        @JsonProperty("tag")
        public String tag; // e.g., "div", "hr", "note"
        @JsonProperty("text")
        public FeishuTextContent text; // For "div"
        @JsonProperty("content") // For "markdown" element directly under "elements" or for some "note" elements
        public String content; 
        @JsonProperty("elements") // For "note" tag, which can contain other elements
        public List<FeishuElement> elements;

        // Constructor for simple text element (div)
        public static FeishuElement div(String markdownContent) {
            FeishuElement el = new FeishuElement();
            el.tag = "div";
            el.text = new FeishuTextContent(markdownContent, "lark_md");
            return el;
        }
        
        // Constructor for hr element
        public static FeishuElement hr() {
            FeishuElement el = new FeishuElement();
            el.tag = "hr";
            return el;
        }

        // Constructor for note element with simple markdown content
        public static FeishuElement note(String markdownContent) {
            FeishuElement noteEl = new FeishuElement();
            noteEl.tag = "note";
            FeishuElement contentEl = new FeishuElement();
            contentEl.tag = "lark_md"; // The Go code uses "lark_md" tag directly on content for note's inner element
            contentEl.content = markdownContent; 
            noteEl.elements = Collections.singletonList(contentEl);
            return noteEl;
        }
    }

    // Response structure from Feishu Webhook API
    private static class FeishuApiResponse {
        @JsonProperty("StatusCode")
        public int statusCode; // Note: Go struct field is StatusCode, JSON is usually status_code or statusCode
        @JsonProperty("StatusMessage")
        public String statusMessage;
        @JsonProperty("code") // This seems to be the primary business logic code
        public int code;
        @JsonProperty("data")
        public Object data; // Can be any structure or null
        @JsonProperty("msg")
        public String msg;
    }

    // Method to send the message to Feishu
    private static void postToFeishuV2(String color, String title, String text, String webhookUrl) throws IOException, InterruptedException {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            LOGGER.warning("Feishu webhook URL is empty, notification not sent.");
            return; // Or throw new IllegalArgumentException
        }

        String notifyNote = AppConfig.getNotifyNote();
        if (notifyNote == null || notifyNote.isEmpty()) {
            notifyNote = "AI Proxy Notification"; // Default note
        }

        FeishuMessageV2 payload = new FeishuMessageV2();
        payload.card = new FeishuCard();
        payload.card.config = new FeishuCardConfig(); // Uses defaults: wideScreenMode=true, enableForward=true
        
        payload.card.header = new FeishuHeader();
        payload.card.header.title = new FeishuTextContent(title, "plain_text");
        payload.card.header.template = color;
        
        payload.card.elements = List.of(
            FeishuElement.div(text), // Main message content
            FeishuElement.hr(),      // Separator
            FeishuElement.note(notifyNote) // Note element
        );

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Failed to marshal Feishu payload to JSON", e);
            throw new IOException("Failed to create Feishu JSON payload", e); // Propagate as IOException
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                FeishuApiResponse feishuResp = objectMapper.readValue(response.body(), FeishuApiResponse.class);
                if (feishuResp.code != 0) { // Feishu API often uses 'code' field for business errors
                    LOGGER.severe("Feishu API returned an error: Code=" + feishuResp.code + ", Msg=" + feishuResp.msg + 
                                  ", StatusMessage=" + feishuResp.statusMessage + ", HTTPStatus=" + response.statusCode());
                    // Optionally throw a custom exception here
                } else {
                    LOGGER.info("Feishu notification sent successfully for title: " + title + ". Feishu Msg: " + feishuResp.msg);
                }
            } catch (JsonProcessingException e) {
                 LOGGER.log(Level.WARNING, "Failed to parse Feishu response JSON: " + response.body(), e);
                 // Treat as success if HTTP status was OK but response parsing failed, or re-throw
            }
        } else {
            LOGGER.severe("Feishu notification failed with HTTP status: " + response.statusCode() + " and body: " + response.body());
            // Optionally throw a custom exception here
        }
    }
    
    /**
     * Shuts down the asynchronous notifier executor.
     * Call this method during application shutdown to ensure all pending notifications are attempted.
     */
    public static void shutdown() {
        LOGGER.info("Shutting down FeishuNotifier async executor...");
        asyncNotifierExecutor.shutdown();
        try {
            if (!asyncNotifierExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warning("FeishuNotifier async executor did not terminate in 10 seconds. Forcing shutdown...");
                asyncNotifierExecutor.shutdownNow();
                if (!asyncNotifierExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.severe("FeishuNotifier async executor did not terminate.");
                }
            }
        } catch (InterruptedException ie) {
            asyncNotifierExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("FeishuNotifier async executor shut down.");
    }
}
