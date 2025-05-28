package com.example.aiproxy.common.balance;

/**
 * Defines an interface for post-consumption logic after a group's balance has been used.
 */
public interface PostGroupConsumer {
    /**
     * Performs post-consumption actions, such as updating the balance.
     *
     * @param tokenName The name of the token used.
     * @param usage     The amount of balance used.
     * @return The new remaining balance after consumption.
     * @throws Exception If an error occurs during the post-consumption process.
     */
    double postGroupConsume(String tokenName, double usage) throws Exception;
}
