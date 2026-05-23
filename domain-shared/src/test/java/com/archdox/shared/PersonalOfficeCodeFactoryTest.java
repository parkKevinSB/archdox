package com.archdox.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PersonalOfficeCodeFactoryTest {
    @Test
    void createsStablePersonalOfficeCode() {
        assertEquals("personal-42", PersonalOfficeCodeFactory.fromUserId(42));
    }

    @Test
    void rejectsNonPositiveUserId() {
        assertThrows(IllegalArgumentException.class, () -> PersonalOfficeCodeFactory.fromUserId(0));
    }
}
