package com.badminton.mes.module.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.badminton.mes.common.core.GlobalErrorCodeConstants;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.common.security.SecurityContextHolder;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import com.badminton.mes.module.rag.dal.entity.RagTenantBinding;
import com.badminton.mes.module.rag.dal.repository.RagTenantBindingRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 租户解析测试。
 *
 * <p>三条失败路径必须能区分：前端要据此给出不同提示（重新登录 / 找管理员开通 / 权限被停用）。
 * 缺少任何一条都会让用户看到笼统的「操作失败」。
 */
@ExtendWith(MockitoExtension.class)
class RagTenantResolverTest {

    /** 测试用业务用户主键 */
    private static final Long USER_ID = 1001L;

    @Mock
    private RagTenantBindingRepository bindingRepository;

    @InjectMocks
    private RagTenantResolver resolver;

    @AfterEach
    void clearContext() {
        // ThreadLocal 必须清理：测试线程会被复用，残留上下文会污染后续用例
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("已绑定且启用时返回租户标识")
    void shouldReturnTenantWhenBound() {
        SecurityContextHolder.set("token", loginUser(USER_ID));
        when(bindingRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(binding(USER_ID, "user_1", true)));

        assertThat(resolver.resolveTenantId()).isEqualTo("user_1");
    }

    @Test
    @DisplayName("未绑定时抛出租户未绑定，而不是返回空值")
    void shouldRejectUnboundUser() {
        SecurityContextHolder.set("token", loginUser(USER_ID));
        when(bindingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveTenantId())
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.TENANT_NOT_BOUND);
    }

    @Test
    @DisplayName("绑定已禁用时抛出租户已禁用，与未绑定区分")
    void shouldRejectDisabledBinding() {
        SecurityContextHolder.set("token", loginUser(USER_ID));
        when(bindingRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(binding(USER_ID, "user_1", false)));

        assertThatThrownBy(() -> resolver.resolveTenantId())
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.TENANT_DISABLED);
    }

    @Test
    @DisplayName("无登录上下文时抛出未授权，不返回任何默认租户")
    void shouldRejectWithoutLoginContext() {
        assertThatThrownBy(() -> resolver.resolveTenantId())
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(GlobalErrorCodeConstants.UNAUTHORIZED);

        // 未登录时不应该去查库：既无意义也会浪费一次查询
        verifyNoMoreInteractions(bindingRepository);
    }

    @Test
    @DisplayName("绑定记录缺租户标识时按未绑定处理")
    void shouldRejectBindingWithoutTenantId() {
        SecurityContextHolder.set("token", loginUser(USER_ID));
        when(bindingRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(binding(USER_ID, "  ", true)));

        assertThatThrownBy(() -> resolver.resolveTenantId())
                .isInstanceOf(ServiceException.class)
                .extracting(error -> ((ServiceException) error).getErrorCode())
                .isEqualTo(RagErrorCodeConstants.TENANT_NOT_BOUND);
    }

    @Test
    @DisplayName("解析时按登录用户主键查库，不使用其他来源")
    void shouldQueryByLoginUserId() {
        SecurityContextHolder.set("token", loginUser(USER_ID));
        when(bindingRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(binding(USER_ID, "user_2", true)));

        assertThat(resolver.resolveTenantId()).isEqualTo("user_2");
        verify(bindingRepository).findByUserId(USER_ID);
    }

    /**
     * 构造登录用户。
     *
     * @param userId 用户主键
     * @return 登录用户
     */
    private LoginUser loginUser(Long userId) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUserNo("demo");
        user.setUserName("演示用户");
        return user;
    }

    /**
     * 构造绑定记录。
     *
     * @param userId   用户主键
     * @param tenantId 租户标识
     * @param enabled  是否启用
     * @return 绑定记录
     */
    private RagTenantBinding binding(Long userId, String tenantId, boolean enabled) {
        RagTenantBinding entity = new RagTenantBinding();
        entity.setUserId(userId);
        entity.setTenantId(tenantId);
        entity.setEnabled(enabled);
        return entity;
    }
}
