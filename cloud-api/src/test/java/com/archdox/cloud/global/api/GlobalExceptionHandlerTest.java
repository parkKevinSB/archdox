package com.archdox.cloud.global.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void unexpected_exception_returns_generic_500_without_internal_message() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleUnexpected(new IllegalStateException("database password leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Unexpected server error");
        assertThat(response.getBody().message()).doesNotContain("database password leaked");
    }
}
