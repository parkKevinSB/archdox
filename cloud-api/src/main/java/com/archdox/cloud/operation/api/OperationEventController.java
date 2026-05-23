package com.archdox.cloud.operation.api;

import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.dto.OperationEventResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operation-events")
public class OperationEventController {
    private final OperationEventService service;

    public OperationEventController(OperationEventService service) {
        this.service = service;
    }

    @GetMapping
    public List<OperationEventResponse> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String workflowType,
            @RequestParam(required = false) String workflowKey,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) Integer limit
    ) {
        return service.list(eventType, workflowType, workflowKey, resourceType, resourceId, limit);
    }
}
