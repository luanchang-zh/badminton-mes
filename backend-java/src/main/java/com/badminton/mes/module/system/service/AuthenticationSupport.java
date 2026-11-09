package com.badminton.mes.module.system.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

import com.badminton.mes.common.enums.CommonStatusEnum;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;
import com.badminton.mes.module.system.controller.vo.AuthLoginRespVO;
import com.badminton.mes.module.system.dal.entity.RoleEntity;
import com.badminton.mes.module.system.dal.entity.UserEntity;
import com.badminton.mes.module.system.dal.entity.UserRoleEntity;
import com.badminton.mes.module.system.dal.redis.LoginSessionRedisDAO;
import com.badminton.mes.module.system.dal.redis.SystemRedisKeyConstants;
import com.badminton.mes.module.system.dal.repository.RoleRepository;
import com.badminton.mes.module.system.dal.repository.UserRepository;
import com.badminton.mes.module.system.dal.repository.UserRoleRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 普通登录和微信登录共享的账号校验、角色加载与会话创建能力。
 *
 * @author Codex
 * @date 2026/07/15
 */
@Component
public class AuthenticationSupport {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationSupport.class);

    private static final int TOKEN_BYTES = 16;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;

    private final UserRoleRepository userRoleRepository;

    private final RoleRepository roleRepository;

    private final LoginSessionRedisDAO loginSessionRedisDAO;

    private final PasswordEncoder passwordEncoder;

    /**
     * 构造认证公共能力。
     *
     * @param userRepository 用户 Repository
     * @param userRoleRepository 用户角色 Repository
     * @param roleRepository 角色 Repository
     * @param loginSessionRedisDAO 登录会话 DAO
     * @param passwordEncoder 密码编码器
     */
    public AuthenticationSupport(UserRepository userRepository, UserRoleRepository userRoleRepository,
                                 RoleRepository roleRepository, LoginSessionRedisDAO loginSessionRedisDAO,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.loginSessionRedisDAO = loginSessionRedisDAO;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 校验工号密码和账号状态。
     *
     * @param userNo 工号
     * @param password 明文密码
     * @return 有效用户
     */
    public UserEntity authenticate(String userNo, String password) {
        if (loginSessionRedisDAO.isLoginLocked(userNo)) {
            throw new ServiceException(SystemErrorCodeConstants.LOGIN_LOCKED);
        }
        UserEntity user = userRepository.findByUserNoAndDeletedFalse(userNo).orElse(null);
        if (user == null) {
            throw loginFail(userNo, "账户不存在");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw loginFail(userNo, "密码错误");
        }
        validateEnabled(user);
        loginSessionRedisDAO.clearLoginFail(userNo);
        return user;
    }

    /**
     * 按用户主键加载启用用户，供已绑定微信身份登录。
     *
     * @param userId 用户主键
     * @return 有效用户
     */
    public UserEntity loadEnabledUser(Long userId) {
        UserEntity user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ServiceException(SystemErrorCodeConstants.USER_NOT_EXISTS));
        validateEnabled(user);
        return user;
    }

    /**
     * 为有效用户创建 Redis 会话并返回普通登录响应。
     *
     * @param user 有效用户
     * @return 登录响应
     */
    public AuthLoginRespVO createSession(UserEntity user) {
        List<RoleEntity> roles = loadEnabledRoles(user.getId());
        LoginUser loginUser = buildLoginUser(user, roles);
        String token = generateToken();
        loginSessionRedisDAO.createSession(token, loginUser);

        AuthLoginRespVO response = new AuthLoginRespVO();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUserNo(user.getUserNo());
        response.setUserName(user.getUserName());
        response.setRoleCodes(loginUser.getRoleCodes());
        return response;
    }

    /**
     * 查询用户当前有效且启用的角色编码。人脸登录需要在发会话前拒绝 ADMIN。
     *
     * @param userId 用户主键
     * @return 角色编码，按角色 id 升序
     */
    public List<String> loadEnabledRoleCodes(Long userId) {
        return loadEnabledRoles(userId).stream().map(RoleEntity::getRoleCode).toList();
    }

    private void validateEnabled(UserEntity user) {
        if (!CommonStatusEnum.ENABLED.getStatus().equals(user.getStatus())) {
            throw new ServiceException(SystemErrorCodeConstants.LOGIN_USER_DISABLED);
        }
    }

    private List<RoleEntity> loadEnabledRoles(Long userId) {
        List<Long> roleIds = userRoleRepository.findByUserIdAndDeletedFalse(userId).stream()
                .map(UserRoleEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findByIdInAndDeletedFalseOrderByIdAsc(roleIds).stream()
                .filter(role -> CommonStatusEnum.ENABLED.getStatus().equals(role.getStatus()))
                .toList();
    }

    private LoginUser buildLoginUser(UserEntity user, List<RoleEntity> roles) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUserNo(user.getUserNo());
        loginUser.setUserName(user.getUserName());
        loginUser.setWorkshopId(user.getWorkshopId());
        loginUser.setLineId(user.getLineId());
        loginUser.setRoleCodes(roles.stream().map(RoleEntity::getRoleCode).toList());
        return loginUser;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private ServiceException loginFail(String userNo, String reason) {
        long failCount = loginSessionRedisDAO.recordLoginFail(userNo);
        logger.warn("[登录失败] userNo: {}, 原因: {}", userNo, reason);
        if (failCount >= SystemRedisKeyConstants.LOGIN_FAIL_MAX) {
            return new ServiceException(SystemErrorCodeConstants.LOGIN_LOCKED);
        }
        return new ServiceException(SystemErrorCodeConstants.LOGIN_CREDENTIALS_INVALID);
    }
}
