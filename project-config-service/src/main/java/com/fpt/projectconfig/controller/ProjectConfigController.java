package com.fpt.projectconfig.controller;

import com.fpt.projectconfig.dto.request.CreateConfigRequest;
import com.fpt.projectconfig.dto.request.UpdateConfigRequest;
import com.fpt.projectconfig.dto.response.ApiResponse;
import com.fpt.projectconfig.dto.response.ConfigResponse;
import com.fpt.projectconfig.dto.response.VerificationResponse;
import com.fpt.projectconfig.service.ProjectConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller cho Project Config API (UC30-35)
 */
@RestController
@RequestMapping("/api/project-configs")
@RequiredArgsConstructor
public class ProjectConfigController {

    private final ProjectConfigService projectConfigService;

    /**
     * UC30: Create Project Config
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConfigResponse> createConfig(@Valid @RequestBody CreateConfigRequest request,
                                                      Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = extractRoles(authentication);
        
        ConfigResponse response = projectConfigService.createConfig(request, userId, roles);
        return ApiResponse.success(response);
    }

    /**
     * UC31: Get Project Config by Group ID
     */
    @GetMapping("/group/{groupId}")
    public ApiResponse<ConfigResponse> getConfigByGroup(@PathVariable Long groupId,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = extractRoles(authentication);
        
        ConfigResponse response = projectConfigService.getConfig(groupId, userId, roles);
        return ApiResponse.success(response);
    }

    /**
     * UC32: Update Project Config
     */
    @PutMapping("/{configId}")
    public ApiResponse<ConfigResponse> updateConfig(@PathVariable UUID configId,
                                                      @Valid @RequestBody UpdateConfigRequest request,
                                                      Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = extractRoles(authentication);
        
        ConfigResponse response = projectConfigService.updateConfig(configId, request, userId, roles);
        return ApiResponse.success(response);
    }

    /**
     * UC33: Delete Project Config (Soft Delete)
     */
    @DeleteMapping("/{configId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConfig(@PathVariable UUID configId,
                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = extractRoles(authentication);
        
        projectConfigService.deleteConfig(configId, userId, roles);
    }

    /**
     * UC34: Verify Project Config
     */
    @PostMapping("/{configId}/verify")
    public ApiResponse<VerificationResponse> verifyConfig(@PathVariable UUID configId,
                                                            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = extractRoles(authentication);
        
        VerificationResponse response = projectConfigService.verifyConfig(configId, userId, roles);
        return ApiResponse.success(response);
    }

    /**
     * UC35: Restore Deleted Config (Admin only)
     */
    @PostMapping("/{configId}/restore")
    public ApiResponse<ConfigResponse> restoreConfig(@PathVariable UUID configId,
                                                       Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = extractRoles(authentication);
        
        ConfigResponse response = projectConfigService.restoreConfig(configId, userId, roles);
        return ApiResponse.success(response);
    }

    private List<String> extractRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }
}
