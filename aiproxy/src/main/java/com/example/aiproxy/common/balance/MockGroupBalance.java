package com.example.aiproxy.common.balance;

// Assuming model.GroupCache will be translated to com.example.aiproxy.model.GroupCache
import com.example.aiproxy.model.GroupCache; // Placeholder import

/**
 * Mock implementation of GroupBalance and PostGroupConsumer.
 */
public class MockGroupBalance implements GroupBalance, PostGroupConsumer {

    private static final double MOCK_BALANCE = 10000000.0;

    public MockGroupBalance() {
        // Constructor
    }

    @Override
    public BalanceResult getGroupRemainBalance(GroupCache group) {
        // Returns a fixed mock balance and itself as the consumer.
        return new BalanceResult(MOCK_BALANCE, this);
    }

    @Override
    public double postGroupConsume(String tokenName, double usage) {
        // In this mock implementation, simply returns the usage.
        // It doesn't actually deduct from any balance.
        System.out.println("MockGroupBalance: Consumed " + usage + " for token " + tokenName + ". Current mock balance would notionally be " + (MOCK_BALANCE - usage) + " if stateful.");
        return MOCK_BALANCE - usage; // Or simply return MOCK_BALANCE if it's meant to be stateless. The Go code returns 'usage', which seems odd.
                                     // Let's assume the intention is to return the *new* remaining balance.
                                     // If it strictly returns 'usage', then: return usage;
                                     // Given the context, returning the new balance makes more sense.
                                     // The Go code `return usage, nil` is strange for `PostGroupConsume`'s documented return of `(float64, error)` where float64 is new balance.
                                     // Let's stick to what makes sense for a balance system: new remaining balance.
    }
}
