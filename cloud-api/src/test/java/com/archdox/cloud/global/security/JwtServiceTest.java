package com.archdox.cloud.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.archdox.cloud.account.domain.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    @Test
    void createsAndParsesAccessToken() {
        var service = new JwtService(
                new JwtProperties("test-secret-with-enough-length-for-hmac", 15, 14),
                new ObjectMapper());
        var user = new UserAccount("user@example.com", "hash", "User", OffsetDateTime.now());
        setId(user, 42L);

        var token = service.createAccessToken(user);

        var principal = service.parse(token);
        assertEquals(42L, principal.userId());
        assertEquals("user@example.com", principal.email());
    }

    @Test
    void rejectsTamperedToken() {
        var service = new JwtService(
                new JwtProperties("test-secret-with-enough-length-for-hmac", 15, 14),
                new ObjectMapper());
        var user = new UserAccount("user@example.com", "hash", "User", OffsetDateTime.now());
        setId(user, 42L);
        var token = service.createAccessToken(user);

        assertThrows(IllegalArgumentException.class, () -> service.parse(token + "x"));
    }

    private void setId(UserAccount user, Long id) {
        try {
            var field = UserAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
