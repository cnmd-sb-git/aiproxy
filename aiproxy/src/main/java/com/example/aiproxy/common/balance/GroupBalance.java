package com.example.aiproxy.common.balance;

// Assuming model.GroupCache will be translated to com.example.aiproxy.model.GroupCache
import com.example.aiproxy.model.GroupCache; // Placeholder import

/**
 * Defines an interface for retrieving the remaining balance for a group.
 */
public interface GroupBalance {
    /**
     * Gets the remaining balance for a given group.
     *
     * @param group The group cache object.
     * @return A BalanceResult containing the remaining balance and a consumer for post-consumption logic.
     * @throws Exception If an error occurs while fetching the balance.
     */
    BalanceResult getGroupRemainBalance(GroupCache group) throws Exception;

    /**
     * A simple class to hold the result of getGroupRemainBalance,
     * as Java methods can't directly return multiple values like Go.
     */
    class BalanceResult {
        private final double remainingBalance;
        private final PostGroupConsumer postGroupConsumer;

        public BalanceResult(double remainingBalance, PostGroupConsumer postGroupConsumer) {
            this.remainingBalance = remainingBalance;
            this.postGroupConsumer = postGroupConsumer;
        }

        public double getRemainingBalance() {
            return remainingBalance;
        }

        public PostGroupConsumer getPostGroupConsumer() {
            return postGroupConsumer;
        }
    }
}
