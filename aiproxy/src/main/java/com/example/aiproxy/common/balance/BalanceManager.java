package com.example.aiproxy.common.balance;

// Assuming model.GroupCache will be translated to com.example.aiproxy.model.GroupCache
import com.example.aiproxy.model.GroupCache; // Placeholder import

/**
 * Manages and provides access to group balance operations.
 * This class holds a default GroupBalance implementation.
 */
public class BalanceManager {

    private static final GroupBalance mockGroupBalance = new MockGroupBalance();

    // The 'Default' instance can be changed to a different implementation if needed,
    // for example, to a real implementation like SealosGroupBalance.
    private static GroupBalance defaultGroupBalance = mockGroupBalance;

    /**
     * Private constructor to prevent instantiation.
     */
    private BalanceManager() {
    }

    /**
     * Sets the default GroupBalance implementation.
     * @param groupBalance The GroupBalance implementation to set as default.
     */
    public static void setDefaultGroupBalance(GroupBalance groupBalance) {
        if (groupBalance == null) {
            throw new IllegalArgumentException("GroupBalance cannot be null.");
        }
        defaultGroupBalance = groupBalance;
    }

    /**
     * Gets the currently configured default GroupBalance implementation.
     * @return The default GroupBalance implementation.
     */
    public static GroupBalance getDefaultGroupBalance() {
        return defaultGroupBalance;
    }
    
    /**
     * Gets the remaining balance for a given group using the default GroupBalance implementation.
     * This is a convenience method.
     *
     * @param group The group cache object.
     * @return A BalanceResult containing the remaining balance and a consumer for post-consumption logic.
     * @throws Exception If an error occurs while fetching the balance.
     * @see GroupBalance#getGroupRemainBalance(GroupCache)
     * @see GroupBalance.BalanceResult
     */
    public static GroupBalance.BalanceResult getGroupRemainBalance(GroupCache group) throws Exception {
        return defaultGroupBalance.getGroupRemainBalance(group);
    }

    /**
     * Gets the remaining balance for a given group using the mock GroupBalance implementation.
     * This is a convenience method for testing or specific mock scenarios.
     *
     * @param group The group cache object.
     * @return A BalanceResult containing the remaining balance and a consumer for post-consumption logic.
     * @throws Exception If an error occurs while fetching the balance.
     */
    public static GroupBalance.BalanceResult mockGetGroupRemainBalance(GroupCache group) throws Exception {
        return mockGroupBalance.getGroupRemainBalance(group);
    }
}
