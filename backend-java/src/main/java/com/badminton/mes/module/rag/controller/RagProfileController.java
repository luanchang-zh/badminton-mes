package com.badminton.mes.module.rag.controller;

import com.badminton.mes.common.core.CommonResult;
import com.badminton.mes.common.security.SecurityContextHolder;
import com.badminton.mes.module.rag.dal.repository.RagTenantBindingRepository;
import org.springframework.web.bind.annotation.*;

/** 只读开通状态；不连接尚未部署的上游，也不向浏览器暴露内部地址和密钥。 */
@RestController
@RequestMapping("/api/rag/profile")
public class RagProfileController {
    private final RagTenantBindingRepository bindings;

    public RagProfileController(RagTenantBindingRepository bindings) {
        this.bindings = bindings;
    }

    public record Profile(boolean bound, boolean enabled, boolean canManageDocuments) { }

    @GetMapping
    public CommonResult<Profile> profile() {
        var user = SecurityContextHolder.getRequiredLoginUser();
        var binding = bindings.findByUserId(user.getUserId());
        boolean enabled = binding.filter(b -> Boolean.TRUE.equals(b.getEnabled())).isPresent();
        boolean manager = user.getRoleCodes().stream()
                .anyMatch(role -> role.equals("ADMIN") || role.equals("CRAFT_ENGINEER"));
        return CommonResult.success(new Profile(binding.isPresent(), enabled, enabled && manager));
    }
}
