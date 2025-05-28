package com.example.aiproxy.common.balance;

import com.example.aiproxy.common.RedisHelper; // Assuming RedisHelper is in this package
import com.example.aiproxy.model.GroupCache; // Placeholder from previous step
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisException;


import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SealosGroupBalance implements GroupBalance {

    private static final Logger LOGGER = Logger.getLogger(SealosGroupBalance.class.getName());

    private static final String DEFAULT_ACCOUNT_URL = "http://account-service.account-system.svc.cluster.local:2333";
    private static final long BALANCE_PRECISION_FACTOR = 1_000_000L; // For converting between int64 and float64 representation
    private static final BigDecimal BIG_DECIMAL_BALANCE_PRECISION_FACTOR = BigDecimal.valueOf(BALANCE_PRECISION_FACTOR);
    private static final BigDecimal MIN_CONSUME_AMOUNT_INTERNAL = BigDecimal.ONE; // Smallest unit for consumption
    private static final String APP_TYPE = "LLM-TOKEN";
    private static final String SEALOS_REQUESTER = "sealos-admin";
    private static final String SEALOS_GROUP_BALANCE_KEY_FORMAT = "sealos:balance:%s";
    private static final String SEALOS_USER_REAL_NAME_KEY_FORMAT = "sealos:realName:%s";
    private static final int GET_BALANCE_RETRY_COUNT = 3;
    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(5);

    private static String jwtToken; // Holds the global JWT token
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_HTTP_TIMEOUT)
            .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Configuration flags (can be set via System properties or a proper config mechanism)
    private static boolean sealosRedisCacheEnable = Boolean.parseBoolean(System.getenv().getOrDefault("BALANCE_SEALOS_REDIS_CACHE_ENABLE", "true"));
    private static Duration sealosCacheExpireTime = Duration.ofMinutes(3);
    private static boolean sealosCheckRealNameEnable = Boolean.parseBoolean(System.getenv().getOrDefault("BALANCE_SEALOS_CHECK_REAL_NAME_ENABLE", "false"));
    private static double sealosNoRealNameUsedAmountLimit = Double.parseDouble(System.getenv().getOrDefault("BALANCE_SEALOS_NO_REAL_NAME_USED_AMOUNT_LIMIT", "1.0"));

    private final String accountServiceURL;

    // Lua script for atomic decrement in Redis
    // KEYS[1] = balance_key, ARGV[1] = amount_to_decrease
    private static final String DECREASE_BALANCE_SCRIPT =
            "local balance = redis.call('HGet', KEYS[1], 'b')\n" +
            "if balance then\n" +
            "  redis.call('HSet', KEYS[1], 'b', balance - ARGV[1])\n" +
            "end\n" +
            "return redis.status_reply('OK')";
    private static String decreaseBalanceScriptSha;


    public SealosGroupBalance(String accountURL) {
        this.accountServiceURL = (accountURL == null || accountURL.isEmpty()) ? DEFAULT_ACCOUNT_URL : accountURL;
    }

    /**
     * Initializes the Sealos balance system with a JWT key and account URL.
     * Sets this instance as the default in BalanceManager.
     *
     * @param jwtKey     The secret key for signing JWTs.
     * @param accountURL The URL for the Sealos account service.
     * @throws Exception if token generation fails.
     */
    public static void initSealos(String jwtKey, String accountURL) throws Exception {
        if (jwtKey == null || jwtKey.isEmpty()) {
            throw new IllegalArgumentException("JWT Key cannot be null or empty for SealosGroupBalance initialization.");
        }
        try {
            jwtToken = generateSealosToken(jwtKey);
            SealosGroupBalance sealosInstance = new SealosGroupBalance(accountURL);
            BalanceManager.setDefaultGroupBalance(sealosInstance); // Set this as the default
            LOGGER.info("SealosGroupBalance initialized and set as default.");

            // Pre-load Lua script if Redis is enabled
            if (RedisHelper.isRedisEnabled()) {
                try (Jedis jedis = RedisHelper.getJedisResource()) { // Assuming RedisHelper provides getJedisResource()
                    if (jedis != null) {
                        decreaseBalanceScriptSha = jedis.scriptLoad(DECREASE_BALANCE_SCRIPT);
                        LOGGER.info("Lua script for balance decrease loaded into Redis. SHA: " + decreaseBalanceScriptSha);
                    } else {
                         LOGGER.warning("Jedis resource is null, cannot load Lua script.");
                    }
                } catch (JedisException e) {
                    LOGGER.log(Level.SEVERE, "Failed to load Lua script into Redis: " + e.getMessage(), e);
                    // Continue without script caching, eval will be used.
                    decreaseBalanceScriptSha = null;
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize SealosGroupBalance: " + e.getMessage(), e);
            throw e;
        }
    }
    
    private static Jedis getJedisResourceFromHelper() {
        // This is a placeholder. RedisHelper should ideally provide a method to get a Jedis resource from its pool.
        // For now, let's assume RedisHelper.getJedisResource() exists or implement a simple one.
        // This is a simplified way; RedisHelper should manage the pool.
        if (RedisHelper.isRedisEnabled()) {
             // Assuming RedisHelper has a method like this.
             // return RedisHelper.getJedisPool().getResource(); 
             // If RedisHelper only has static methods for get/set/del, direct Jedis resource might not be exposed.
             // For script loading, we need a direct Jedis instance.
             // This part of design might need refinement based on RedisHelper's final API.
             LOGGER.warning("getJedisResourceFromHelper: Needs proper implementation in RedisHelper or direct JedisPool access.");
        }
        return null; // Fallback if not available
    }


    private static String generateSealosToken(String key) {
        SecretKey secretKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        JwtBuilder builder = Jwts.builder()
                .claim("requester", SEALOS_REQUESTER)
                .notBefore(Date.from(now))
                // .setIssuedAt(Date.from(now)) // Optional: set issued at
                // .setExpiration(Date.from(now.plus(1, ChronoUnit.HOURS))) // Optional: set expiration
                .signWith(secretKey, Jwts.SIG.HS256);
        return builder.compact();
    }

    // POJO for API responses/requests
    private static class SealosGetGroupBalanceResp {
        @JsonProperty("userUID")
        public String userUID;
        @JsonProperty("error")
        public String error;
        @JsonProperty("balance")
        public long balance; // This is the int64 representation
    }

    private static class SealosPostGroupConsumeReq {
        @JsonProperty("namespace")
        public String namespace;
        @JsonProperty("appType")
        public String appType;
        @JsonProperty("appName")
        public String appName;
        @JsonProperty("userUID")
        public String userUID;
        @JsonProperty("amount")
        public long amount; // This is the int64 representation
    }

    private static class SealosPostGroupConsumeResp {
        @JsonProperty("error")
        public String error;
    }
    
    private static class SealosGetRealNameInfoResp {
        @JsonProperty("isRealName")
        public boolean isRealName;
        @JsonProperty("error")
        public String error;
    }


    // Represents the structure stored in Redis for balance cache
    private static class SealosCacheData {
        // Field names match 'redis:"u"' and 'redis:"b"' implicitly via Jackson if HGetAll returns map
        // Or, if using direct HGet, map manually.
        // For simplicity with HSet/HGetAll using Map<String, String> with Jedis:
        public String u; // userUID
        public String b; // balance (as string, to be parsed to long)

        public SealosCacheData() {} // For Jackson

        public SealosCacheData(String userUID, long balance) {
            this.u = userUID;
            this.b = String.valueOf(balance);
        }

        public long getBalanceAsLong() {
            return Long.parseLong(b);
        }
    }

    // --- Interface Implementations and Helper Methods will follow ---
    // Placeholder for getGroupRemainBalance
    @Override
    public BalanceResult getGroupRemainBalance(GroupCache group) throws Exception {
        // Implementation will be added in the next step
        if (group == null || group.getId() == null || group.getId().isEmpty()) {
            throw new IllegalArgumentException("Group and Group ID cannot be null or empty.");
        }
        
        List<Exception> accumulatedErrors = new ArrayList<>();
        for (int i = 0; i < GET_BALANCE_RETRY_COUNT; i++) {
            try {
                FetchBalanceResult fetched = fetchBalanceWithRetries(group.getId());
                
                // Real name check
                if (sealosCheckRealNameEnable) {
                    // Assuming GroupCache has a method like getUsedAmount() - this needs to be added to GroupCache placeholder
                    // double usedAmount = group.getUsedAmount(); // This method needs to exist in GroupCache
                    // For now, let's assume usedAmount is 0 or not checked if GroupCache doesn't have it yet.
                    double usedAmount = 0.0; // Placeholder
                    if (usedAmount > sealosNoRealNameUsedAmountLimit && !checkRealName(fetched.userUID)) {
                         throw new NoRealNameUsedAmountLimitException("User " + fetched.userUID + " has exceeded usage limit for non-real-name verified users.");
                    }
                }

                double floatBalance = BigDecimal.valueOf(fetched.balance)
                                             .divide(BIG_DECIMAL_BALANCE_PRECISION_FACTOR, 6, RoundingMode.HALF_UP)
                                             .doubleValue();
                
                SealosPostGroupConsumer consumer = new SealosPostGroupConsumer(
                    this.accountServiceURL, 
                    group.getId(), 
                    fetched.userUID
                );
                return new BalanceResult(floatBalance, consumer);
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Attempt " + (i + 1) + " to get group balance for " + group.getId() + " failed: " + e.getMessage(), e);
                accumulatedErrors.add(e);
                if (i < GET_BALANCE_RETRY_COUNT - 1) {
                    try {
                        TimeUnit.SECONDS.sleep(1); // Wait before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie; // Propagate if interrupted during sleep
                    }
                }
            } catch (NoRealNameUsedAmountLimitException e) {
                throw e; // Propagate this specific exception immediately
            }
        }
        // All retries failed
        throw new IOException("Failed to get group balance after " + GET_BALANCE_RETRY_COUNT + " attempts. Errors: " + accumulatedErrors, accumulatedErrors.get(accumulatedErrors.size()-1));
    }

    private static class FetchBalanceResult {
        long balance;
        String userUID;
        FetchBalanceResult(long balance, String userUID) {
            this.balance = balance;
            this.userUID = userUID;
        }
    }
    
    // This method combines the logic from the Go version's getGroupRemainBalance (the one calling fetch and cache)
    private FetchBalanceResult fetchBalanceWithRetries(String groupId) throws IOException, InterruptedException {
        // 1. Try cache
        if (sealosRedisCacheEnable && RedisHelper.isRedisEnabled()) {
            try {
                SealosCacheData cachedData = cacheGetGroupBalance(groupId);
                if (cachedData != null && cachedData.u != null && !cachedData.u.isEmpty()) {
                    LOGGER.fine("Balance for group " + groupId + " found in cache.");
                    return new FetchBalanceResult(cachedData.getBalanceAsLong(), cachedData.u);
                }
            } catch (JedisException e) {
                LOGGER.log(Level.WARNING, "Failed to get group balance from cache for " + groupId + ": " + e.getMessage(), e);
                // Proceed to API fetch
            }
        }

        // 2. Fetch from API
        LOGGER.fine("Fetching balance from API for group: " + groupId);
        FetchBalanceResult apiResult = fetchBalanceFromAPI(groupId);

        // 3. Set cache
        if (sealosRedisCacheEnable && RedisHelper.isRedisEnabled()) {
            try {
                cacheSetGroupBalance(groupId, apiResult.balance, apiResult.userUID);
            } catch (JedisException e) {
                LOGGER.log(Level.WARNING, "Failed to set group balance cache for " + groupId + ": " + e.getMessage(), e);
                // Non-critical, error is already logged.
            }
        }
        return apiResult;
    }
    
    // Placeholder for custom exception
    public static class NoRealNameUsedAmountLimitException extends Exception {
        public NoRealNameUsedAmountLimitException(String message) {
            super(message);
        }
    }

    // --- Helper methods for API interaction and Caching ---

    private FetchBalanceResult fetchBalanceFromAPI(String group) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/admin/v1alpha1/account-with-workspace?namespace=%s", this.accountServiceURL, group)))
                .header("Authorization", "Bearer " + jwtToken)
                .timeout(DEFAULT_HTTP_TIMEOUT)
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String errorBody = "";
            try (InputStream responseBody = response.body()) {
                errorBody = new String(responseBody.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new IOException("Failed to fetch balance from API for group " + group + ". Status: " + response.statusCode() + ", Body: " + errorBody);
        }

        try (InputStream responseBody = response.body()) {
            SealosGetGroupBalanceResp respBody = objectMapper.readValue(responseBody, SealosGetGroupBalanceResp.class);
            if (respBody.error != null && !respBody.error.isEmpty()) {
                throw new IOException("API error while fetching balance for group " + group + ": " + respBody.error);
            }
            return new FetchBalanceResult(respBody.balance, respBody.userUID);
        }
    }

    private SealosCacheData cacheGetGroupBalance(String group) throws JedisException {
        if (!RedisHelper.isRedisEnabled() || !sealosRedisCacheEnable) return null;

        try (Jedis jedis = RedisHelper.getJedisResource()) { // Assuming RedisHelper.getJedisResource()
             if (jedis == null) {
                LOGGER.warning("Jedis resource is null in cacheGetGroupBalance.");
                return null;
            }
            Map<String, String> result = jedis.hgetAll(String.format(SEALOS_GROUP_BALANCE_KEY_FORMAT, group));
            if (result == null || result.isEmpty() || !result.containsKey("u") || !result.containsKey("b")) {
                return null; // Cache miss or incomplete data
            }
            SealosCacheData cacheData = new SealosCacheData();
            cacheData.u = result.get("u");
            cacheData.b = result.get("b");
            return cacheData;
        }
    }

    private void cacheSetGroupBalance(String group, long balance, String userUID) throws JedisException {
        if (!RedisHelper.isRedisEnabled() || !sealosRedisCacheEnable) return;

        try (Jedis jedis = RedisHelper.getJedisResource()) {
             if (jedis == null) {
                LOGGER.warning("Jedis resource is null in cacheSetGroupBalance.");
                return;
            }
            Pipeline pipeline = jedis.pipelined();
            String key = String.format(SEALOS_GROUP_BALANCE_KEY_FORMAT, group);
            Map<String, String> data = new HashMap<>();
            data.put("u", userUID);
            data.put("b", String.valueOf(balance));
            pipeline.hmset(key, data);
            
            Random random = new Random();
            long jitter = (random.nextInt(11) - 5); // -5 to +5 seconds jitter
            Duration effectiveExpireTime = sealosCacheExpireTime.plusSeconds(jitter);
            pipeline.expire(key, effectiveExpireTime.getSeconds());
            pipeline.sync();
        }
    }
    
    private boolean checkRealName(String userUID) throws IOException, InterruptedException {
        if (sealosRedisCacheEnable && RedisHelper.isRedisEnabled()) {
            try {
                Boolean cachedRealName = cacheGetUserRealName(userUID);
                if (cachedRealName != null) {
                    return cachedRealName;
                }
            } catch (JedisException e) {
                LOGGER.log(Level.WARNING, "Failed to get user real name from cache for " + userUID + ": " + e.getMessage(), e);
            }
        }

        boolean apiRealName = fetchRealNameFromAPI(userUID);

        if (sealosRedisCacheEnable && RedisHelper.isRedisEnabled()) {
            try {
                cacheSetUserRealName(userUID, apiRealName);
            } catch (JedisException e) {
                 LOGGER.log(Level.WARNING, "Failed to set user real name cache for " + userUID + ": " + e.getMessage(), e);
            }
        }
        return apiRealName;
    }

    private Boolean cacheGetUserRealName(String userUID) throws JedisException {
        try (Jedis jedis = RedisHelper.getJedisResource()) {
            if (jedis == null) return null;
            String val = jedis.get(String.format(SEALOS_USER_REAL_NAME_KEY_FORMAT, userUID));
            if (val == null) return null; // Cache miss
            return Boolean.parseBoolean(val);
        }
    }

    private void cacheSetUserRealName(String userUID, boolean realName) throws JedisException {
         try (Jedis jedis = RedisHelper.getJedisResource()) {
             if (jedis == null) return;
            Duration expireTime = realName ? Duration.ofHours(12) : Duration.ofMinutes(1);
            jedis.setex(String.format(SEALOS_USER_REAL_NAME_KEY_FORMAT, userUID), expireTime.getSeconds(), String.valueOf(realName));
        }
    }
    
    private boolean fetchRealNameFromAPI(String userUID) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/admin/v1alpha1/real-name-info?userUID=%s", this.accountServiceURL, userUID)))
                .header("Authorization", "Bearer " + jwtToken)
                .timeout(DEFAULT_HTTP_TIMEOUT)
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
             String errorBody = "";
            try (InputStream responseBody = response.body()) {
                errorBody = new String(responseBody.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new IOException("Failed to fetch real name from API for user " + userUID + ". Status: " + response.statusCode() + ", Body: " + errorBody);
        }
        try (InputStream responseBody = response.body()) {
            SealosGetRealNameInfoResp respBody = objectMapper.readValue(responseBody, SealosGetRealNameInfoResp.class);
             if (respBody.error != null && !respBody.error.isEmpty()) {
                throw new IOException("API error while fetching real name for user " + userUID + ": " + respBody.error);
            }
            return respBody.isRealName;
        }
    }


    // --- Inner class for PostGroupConsumer ---
    private static class SealosPostGroupConsumer implements PostGroupConsumer {
        private final String accountServiceURLConsumer;
        private final String group;
        private final String userUID;

        public SealosPostGroupConsumer(String accountURL, String group, String uid) {
            this.accountServiceURLConsumer = accountURL;
            this.group = group;
            this.userUID = uid;
        }

        @Override
        public double postGroupConsume(String tokenName, double usage) throws Exception {
            BigDecimal usageDecimal = BigDecimal.valueOf(usage);
            BigDecimal amountToConsumeDecimal = usageDecimal.multiply(BIG_DECIMAL_BALANCE_PRECISION_FACTOR).setScale(0, RoundingMode.CEILING);

            if (amountToConsumeDecimal.compareTo(MIN_CONSUME_AMOUNT_INTERNAL) < 0) {
                amountToConsumeDecimal = MIN_CONSUME_AMOUNT_INTERNAL;
            }
            long amountToConsumeLong = amountToConsumeDecimal.longValueExact();

            // 1. Decrease from cache (if enabled)
            if (sealosRedisCacheEnable && RedisHelper.isRedisEnabled()) {
                try {
                    cacheDecreaseGroupBalance(group, amountToConsumeLong);
                } catch (JedisException e) {
                    LOGGER.log(Level.WARNING, "Failed to decrease group balance in cache for " + group + ": " + e.getMessage(), e);
                    // Continue to API call even if cache update fails
                }
            }

            // 2. Post consumption to API
            postConsumeToAPI(amountToConsumeLong, tokenName);
            
            // Return the amount consumed in the external representation (float)
            return amountToConsumeDecimal.divide(BIG_DECIMAL_BALANCE_PRECISION_FACTOR, 6, RoundingMode.HALF_UP).doubleValue();
        }

        private void cacheDecreaseGroupBalance(String group, long amount) throws JedisException {
            try (Jedis jedis = RedisHelper.getJedisResource()) {
                if (jedis == null) {
                     LOGGER.warning("Jedis resource is null in cacheDecreaseGroupBalance.");
                     return;
                }
                String key = String.format(SEALOS_GROUP_BALANCE_KEY_FORMAT, group);
                Object result;
                if (decreaseBalanceScriptSha != null && !decreaseBalanceScriptSha.isEmpty()) {
                    try {
                        result = jedis.evalsha(decreaseBalanceScriptSha, List.of(key), List.of(String.valueOf(amount)));
                    } catch (JedisException e) {
                        // Fallback to EVAL if SCRIPT LOAD failed or SHA disappeared
                        if (e.getMessage() != null && e.getMessage().toUpperCase().contains("NOSCRIPT")) {
                            LOGGER.warning("Lua script SHA not found, falling back to EVAL: " + decreaseBalanceScriptSha);
                            result = jedis.eval(DECREASE_BALANCE_SCRIPT, List.of(key), List.of(String.valueOf(amount)));
                             // Optionally, try to reload the script SHA here if it was missing
                            decreaseBalanceScriptSha = jedis.scriptLoad(DECREASE_BALANCE_SCRIPT);
                        } else {
                            throw e;
                        }
                    }
                } else {
                    result = jedis.eval(DECREASE_BALANCE_SCRIPT, List.of(key), List.of(String.valueOf(amount)));
                }
                LOGGER.fine("Cache decrease result for group " + group + ": " + result);
            }
        }

        private void postConsumeToAPI(long amount, String tokenName) throws IOException, InterruptedException {
            SealosPostGroupConsumeReq reqPayload = new SealosPostGroupConsumeReq();
            reqPayload.namespace = this.group;
            reqPayload.amount = amount;
            reqPayload.appType = APP_TYPE;
            reqPayload.appName = tokenName; // tokenName corresponds to AppName
            reqPayload.userUID = this.userUID;

            String jsonPayload;
            try {
                jsonPayload = objectMapper.writeValueAsString(reqPayload);
            } catch (JsonProcessingException e) {
                throw new IOException("Failed to serialize request for postConsume: " + e.getMessage(), e);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.accountServiceURLConsumer + "/admin/v1alpha1/charge-billing"))
                    .header("Authorization", "Bearer " + jwtToken)
                    .header("Content-Type", "application/json")
                    .timeout(DEFAULT_HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                 String errorBody = "";
                try (InputStream responseBody = response.body()) {
                    errorBody = new String(responseBody.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw new IOException("Failed to post consumption to API for group " + this.group +
                        ". Status: " + response.statusCode() + ", Error: " + errorBody);
            }

            try (InputStream responseBody = response.body()) {
                SealosPostGroupConsumeResp respBody = objectMapper.readValue(responseBody, SealosPostGroupConsumeResp.class);
                if (respBody.error != null && !respBody.error.isEmpty()) {
                    throw new IOException("API error while posting consumption for group " + this.group + ": " + respBody.error);
                }
            }
        }
    } // End of SealosPostGroupConsumer
}
