package com.archdox.cloud.global.api;

import java.util.Map;

public interface ApiException {
    String code();

    String messageKey();

    Map<String, Object> params();
}
