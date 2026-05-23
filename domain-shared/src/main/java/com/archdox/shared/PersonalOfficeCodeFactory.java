package com.archdox.shared;

public final class PersonalOfficeCodeFactory {
    private PersonalOfficeCodeFactory() {
    }

    public static String fromUserId(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return "personal-" + userId;
    }
}
