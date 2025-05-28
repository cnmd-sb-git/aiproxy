package com.example.aiproxy.relay.meta;

import com.example.aiproxy.model.GroupCache; // Assuming GroupCache is in model package
import com.example.aiproxy.model.TokenCache; // Placeholder for TokenCache

import java.time.Instant;

/**
 * Placeholder for relay.meta.Meta.
 * Fields will be added based on the Go definition.
 */
public class Meta {
    public String requestID; // Corresponds to RequestID
    public Instant requestAt;  // Corresponds to RequestAt (time.Time -> Instant)
    public GroupCache group;   // Corresponds to Group (model.GroupCache)
    public TokenCache token;   // Corresponds to Token (model.TokenCache)
    public String originModel; // Corresponds to OriginModel
    public Instant retryAt;    // Corresponds to RetryAt
    public ChannelCache channel; // Corresponds to Channel (model.ChannelCache)
    public String endpoint;    // Corresponds to Endpoint
    public int mode;           // Corresponds to Mode (type not specified in Go, assuming int for now)


    public Meta() {}

    // Basic constructor
    public Meta(String requestID, Instant requestAt, Instant retryAt, GroupCache group, TokenCache token, String originModel, ChannelCache channel, String endpoint, int mode) {
        this.requestID = requestID;
        this.requestAt = requestAt;
        this.retryAt = retryAt;
        this.group = group;
        this.token = token;
        this.originModel = originModel;
        this.channel = channel;
        this.endpoint = endpoint;
        this.mode = mode;
    }

    // Getters and setters can be added as needed
}
