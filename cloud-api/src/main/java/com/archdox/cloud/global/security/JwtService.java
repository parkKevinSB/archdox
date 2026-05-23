package com.archdox.cloud.global.security;

import com.archdox.cloud.account.domain.UserAccount;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JwtProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String createAccessToken(UserAccount user) {
        var now = Instant.now(clock);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("sub", user.id().toString());
        payload.put("email", user.email());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plusSeconds(properties.accessTokenTtlMinutes() * 60).getEpochSecond());
        return sign(Map.of("alg", "HS256", "typ", "JWT"), payload);
    }

    public UserPrincipal parse(String token) {
        var parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT");
        }
        var signingInput = parts[0] + "." + parts[1];
        var expected = URL_ENCODER.encodeToString(hmac(signingInput.getBytes(StandardCharsets.UTF_8)));
        if (!constantTimeEquals(expected, parts[2])) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }
        try {
            var payload = objectMapper.readValue(URL_DECODER.decode(parts[1]), MAP_TYPE);
            var exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now(clock).getEpochSecond() >= exp) {
                throw new IllegalArgumentException("JWT expired");
            }
            return new UserPrincipal(Long.valueOf((String) payload.get("sub")), (String) payload.get("email"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JWT payload", ex);
        }
    }

    private String sign(Map<String, Object> header, Map<String, Object> payload) {
        try {
            var encodedHeader = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(header));
            var encodedPayload = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            var signingInput = encodedHeader + "." + encodedPayload;
            var signature = URL_ENCODER.encodeToString(hmac(signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create JWT", ex);
        }
    }

    private byte[] hmac(byte[] input) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        var result = 0;
        for (var i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }
}
