package com.medops.assistant.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medops.assistant.api.dto.AssistantChatRequest;
import com.medops.assistant.api.dto.AssistantChatResponse;
import com.medops.assistant.application.AssistantService;
import com.medops.shared.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    /**
     * Forwards an authenticated user's chat message to the AI assistant.
     *
     * <p>The user's identity is derived from the authenticated principal only;
     * no user/patient/doctor identifiers are accepted from the client.
     */
    @PostMapping("/assistant/chat")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<ApiResponse<AssistantChatResponse>> chat(
            Authentication authentication,
            @Valid @RequestBody AssistantChatRequest request) {
        AssistantChatResponse response = assistantService.chat(authentication.getName(), request.message());
        return ResponseEntity.ok(ApiResponse.success(response, "Assistant reply"));
    }
}
