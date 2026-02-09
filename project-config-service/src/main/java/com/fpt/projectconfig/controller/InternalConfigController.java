package com.fpt.projectconfig.controller;

import com.fpt.projectconfig.dto.response.ApiResponse;
import com.fpt.projectconfig.dto.response.DecryptedTokensResponse;
import com.fpt.projectconfig.service.ProjectConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal API controller cho service-to-service communication
 * Chỉ được gọi từ Sync Service
 */
@RestController
@RequestMapping("/internal/project-configs")
@RequiredArgsConstructor
public class InternalConfigController {

    private final ProjectConfigService projectConfigService;

    /**
     * Internal API: Get decrypted tokens cho Sync Service
     * Chỉ trả về nếu config state = VERIFIED
     */
    @GetMapping("/{configId}/tokens")
    public ApiResponse<DecryptedTokensResponse> getDecryptedTokens(@PathVariable UUID configId) {
        DecryptedTokensResponse response = projectConfigService.getDecryptedTokens(configId);
        return ApiResponse.success(response);
    }
}
