package com.archdox.cloud.workerchat.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.workerchat.application.ArchDoxWorkerChatService;
import com.archdox.cloud.workerchat.dto.ArchDoxWorkerChatSessionResponse;
import com.archdox.cloud.workerchat.dto.SendArchDoxWorkerChatMessageRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/worker-chat")
public class ArchDoxWorkerChatController {
    private final ArchDoxWorkerChatService chatService;

    public ArchDoxWorkerChatController(ArchDoxWorkerChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ArchDoxWorkerChatSessionResponse open(@PathVariable Long projectId, Authentication authentication) {
        return chatService.open(projectId, principal(authentication));
    }

    @PostMapping("/messages")
    public ArchDoxWorkerChatSessionResponse send(
            @PathVariable Long projectId,
            @Valid @RequestBody SendArchDoxWorkerChatMessageRequest request,
            Authentication authentication
    ) {
        return chatService.send(projectId, request, principal(authentication));
    }

    @PostMapping("/cancel")
    public ArchDoxWorkerChatSessionResponse cancel(@PathVariable Long projectId, Authentication authentication) {
        return chatService.cancelActiveAction(projectId, principal(authentication));
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
