package com.cpptrader.admin.stp;

public final class SelfTradePreventionPolicy {

    private SelfTradePreventionPolicy() {}

    public static final String REJECT_NEW = "REJECT_NEW";
    public static final String CANCEL_OLDEST = "CANCEL_OLDEST";
    public static final String CANCEL_NEWEST = "CANCEL_NEWEST";
    public static final String CANCEL_BOTH = "CANCEL_BOTH";
    public static final String DECREMENT = "DECREMENT";

    public static boolean isValid(String policy) {
        return REJECT_NEW.equals(policy)
                || CANCEL_OLDEST.equals(policy)
                || CANCEL_NEWEST.equals(policy)
                || CANCEL_BOTH.equals(policy)
                || DECREMENT.equals(policy);
    }

    public static String name(String policy) {
        return switch (policy) {
            case REJECT_NEW -> "REJECT_NEW";
            case CANCEL_OLDEST -> "CANCEL_OLDEST";
            case CANCEL_NEWEST -> "CANCEL_NEWEST";
            case CANCEL_BOTH -> "CANCEL_BOTH";
            case DECREMENT -> "DECREMENT";
            default -> "UNKNOWN";
        };
    }
}
