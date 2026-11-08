package com.badminton.mes.module.rag.service;

import java.util.Optional;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.common.security.SecurityContextHolder;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.dal.entity.RagTenantBinding;
import com.badminton.mes.module.rag.dal.repository.RagTenantBindingRepository;

import org.springframework.stereotype.Service;

/**
 * 把当前登录用户解析为 LightRAG 租户标识。
 *
 * <p><b>安全边界在这里。</b>本类只提供不接收租户参数的方法：租户标识的唯一来源是
 * 登录上下文加绑定表。没有任何重载允许调用方指定租户，因此前端无法通过参数越权，
 * 这是签名层面的保证，而不是靠调用方自觉。
 *
 * <p>三种失败情形分别抛出不同错误码，便于前端区分提示：
 * 无登录上下文（未授权）、未绑定、已禁用。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@Service
public class RagTenantResolver {

    /** 租户绑定查询 */
    private final RagTenantBindingRepository bindingRepository;

    /**
     * 构造器注入。
     *
     * @param bindingRepository 租户绑定查询
     */
    public RagTenantResolver(RagTenantBindingRepository bindingRepository) {
        this.bindingRepository = bindingRepository;
    }

    /**
     * 解析当前登录用户对应的租户标识。
     *
     * @return 租户标识，永不为 null 也不为空串
     * @throws ServiceException 未登录、未绑定或绑定已禁用
     */
    public String resolveTenantId() {
        LoginUser loginUser = SecurityContextHolder.getRequiredLoginUser();
        return resolveForUserId(loginUser.getUserId());
    }

    /**
     * 按业务用户主键解析租户标识。
     *
     * <p>包级可见，仅供本类与同包测试使用：对外暴露 Long 参数的入口会给调用方
     * 留下「传别人的 userId」的空间，因此不设为 public。
     *
     * @param userId 业务系统用户主键
     * @return 租户标识
     * @throws ServiceException 未绑定或绑定已禁用
     */
    String resolveForUserId(Long userId) {
        Optional<RagTenantBinding> binding = bindingRepository.findByUserId(userId);
        if (binding.isEmpty()) {
            throw new ServiceException(RagErrorCodeConstants.TENANT_NOT_BOUND);
        }
        RagTenantBinding found = binding.get();
        if (!Boolean.TRUE.equals(found.getEnabled())) {
            throw new ServiceException(RagErrorCodeConstants.TENANT_DISABLED);
        }
        String tenantId = found.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            // 绑定表数据异常：已启用却没有租户标识，按未绑定处理而不是放行
            throw new ServiceException(RagErrorCodeConstants.TENANT_NOT_BOUND);
        }
        return tenantId.trim();
    }
}
