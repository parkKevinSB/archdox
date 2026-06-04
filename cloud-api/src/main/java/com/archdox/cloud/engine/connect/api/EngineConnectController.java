package com.archdox.cloud.engine.connect.api;

import com.archdox.cloud.engine.connect.application.EngineConnectBootstrapWorker;
import com.archdox.cloud.engine.connect.dto.CreateEngineConnectBootstrapRequest;
import com.archdox.cloud.engine.connect.dto.EngineConnectBootstrapResponse;
import com.archdox.cloud.engine.connect.dto.EngineConnectClientResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engine/connect")
public class EngineConnectController {
    private final EngineConnectBootstrapWorker worker;

    public EngineConnectController(EngineConnectBootstrapWorker worker) {
        this.worker = worker;
    }

    @GetMapping("/clients")
    public List<EngineConnectClientResponse> clients() {
        return worker.clients();
    }

    @PostMapping("/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public EngineConnectBootstrapResponse bootstrap(
            Authentication authentication,
            @Valid @RequestBody CreateEngineConnectBootstrapRequest request
    ) {
        return worker.bootstrap((UserPrincipal) authentication.getPrincipal(), request);
    }
}
