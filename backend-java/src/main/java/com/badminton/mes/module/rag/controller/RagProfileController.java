package com.badminton.mes.module.rag.controller;

import com.badminton.mes.common.core.CommonResult;
import com.badminton.mes.common.security.SecurityContextHolder;
import com.badminton.mes.module.rag.service.RagBindingService;
import com.badminton.mes.module.rag.config.RagProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/** 初始化本人的本地会话空间并返回接入状态。 */
@RestController
@RequestMapping("/api/rag/profile")
public class RagProfileController {
    private final RagBindingService bindings;
    private final RagProperties properties;

    public RagProfileController(RagBindingService bindings, RagProperties properties) {
        this.bindings = bindings;
        this.properties = properties;
    }

    public record Profile(boolean bound, boolean enabled, boolean canManageDocuments,
                          boolean serviceConfigured) { }

    @GetMapping
    public CommonResult<Profile> profile() {
        var user = SecurityContextHolder.getRequiredLoginUser();
        var binding = bindings.ensureCurrentBinding();
        boolean enabled = Boolean.TRUE.equals(binding.getEnabled());
        boolean manager = user.getRoleCodes().stream()
                .anyMatch(role -> role.equals("ADMIN") || role.equals("CRAFT_ENGINEER"));
        boolean serviceConfigured = StringUtils.hasText(properties.getBaseUrl())
                && StringUtils.hasText(properties.getInternalApiKey());
        return CommonResult.success(new Profile(true, enabled, enabled && manager,
                serviceConfigured));
    }
}
