package com.badminton.mes.module.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.badminton.mes.common.core.ErrorCode;
import com.badminton.mes.common.core.GlobalErrorCodeConstants;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.RoleCodeConstants;
import com.badminton.mes.module.system.config.InsightFaceProperties;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;
import com.badminton.mes.module.system.controller.vo.AuthLoginRespVO;
import com.badminton.mes.module.system.dal.entity.FaceBindingEntity;
import com.badminton.mes.module.system.dal.entity.UserEntity;
import com.badminton.mes.module.system.dal.entity.WechatUserBindingEntity;
import com.badminton.mes.module.system.dal.redis.FaceLoginFailRedisDAO;
import com.badminton.mes.module.system.dal.redis.LoginSessionRedisDAO;
import com.badminton.mes.module.system.dal.repository.RoleRepository;
import com.badminton.mes.module.system.dal.repository.UserRepository;
import com.badminton.mes.module.system.dal.repository.UserRoleRepository;
import com.badminton.mes.module.system.service.AuthenticationSupport;
import com.badminton.mes.module.system.service.FaceService;
import com.badminton.mes.module.system.service.RoleService;
import com.badminton.mes.module.system.service.WechatUserBindingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

/** 人脸登录：IP 限流、失败计数规则、用户核对与会话发放。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplFaceLoginTest {

    private static final Long USER_ID = 7L;

    private static final String IP = "192.168.1.20";

    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private LoginSessionRedisDAO loginSessionRedisDAO;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationSupport authenticationSupport;

    @Mock
    private RoleService roleService;

    @Mock
    private WechatUserBindingService wechatUserBindingService;

    @Mock
    private FaceService faceService;

    @Mock
    private FaceLoginFailRedisDAO faceLoginFailRedisDAO;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, userRoleRepository, roleRepository, loginSessionRedisDAO,
                passwordEncoder, authenticationSupport, roleService, wechatUserBindingService,
                faceService, faceLoginFailRedisDAO, new InsightFaceProperties());
        lenient().when(faceLoginFailRedisDAO.getFailCount(IP)).thenReturn(0L);
    }

    @Test
    @DisplayName("成功：响应与密码登录一致(含微信字段)，记录登录时间，不清零失败计数")
    void faceLoginSuccess() {
        FaceBindingEntity binding = binding();
        when(faceService.recognize(any())).thenReturn(binding);
        UserEntity user = user(1);
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(user));
        when(authenticationSupport.loadEnabledRoleCodes(USER_ID)).thenReturn(List.of(RoleCodeConstants.OPERATOR));
        AuthLoginRespVO session = new AuthLoginRespVO();
        session.setToken("t");
        session.setUserId(USER_ID);
        when(authenticationSupport.createSession(user)).thenReturn(session);
        WechatUserBindingEntity wechat = new WechatUserBindingEntity();
        wechat.setCreateTime(LocalDateTime.of(2026, 7, 16, 9, 0));
        when(wechatUserBindingService.findActiveByUserId(USER_ID)).thenReturn(Optional.of(wechat));

        AuthLoginRespVO response = service.faceLogin(List.of(jpeg()), IP);

        assertThat(response.getToken()).isEqualTo("t");
        assertThat(response.isWechatBound()).isTrue();
        assertThat(response.getWechatBindingTime()).isEqualTo(wechat.getCreateTime());
        verify(faceService).recordLogin(binding);
        verify(faceLoginFailRedisDAO, never()).recordFail(anyString(), any());
        verifyNoInteractions(loginSessionRedisDAO);
    }

    @Test
    @DisplayName("限流：达到上限直接 A0501，不调用人脸服务")
    void faceLoginRateLimited() {
        when(faceLoginFailRedisDAO.getFailCount(IP)).thenReturn(20L);

        assertServiceError(() -> service.faceLogin(List.of(jpeg()), IP),
                SystemErrorCodeConstants.FACE_LOGIN_TOO_FREQUENT);
        verifyNoInteractions(faceService);
        verify(faceLoginFailRedisDAO, never()).recordFail(anyString(), any());
    }

    @Test
    @DisplayName("计数：识别未通过、张数不符、类型不符都加 1")
    void faceLoginCountsUserSideFailures() {
        when(faceService.recognize(any()))
                .thenThrow(new ServiceException(SystemErrorCodeConstants.FACE_LOGIN_REJECTED));
        MultipartFile text = new MockMultipartFile("image", "a.jpg", "image/jpeg",
                "text".getBytes(StandardCharsets.UTF_8));

        assertServiceError(() -> service.faceLogin(List.of(jpeg()), IP), SystemErrorCodeConstants.FACE_LOGIN_REJECTED);
        assertServiceError(() -> service.faceLogin(List.of(jpeg(), jpeg()), IP),
                SystemErrorCodeConstants.FACE_IMAGE_COUNT_INVALID);
        assertServiceError(() -> service.faceLogin(List.of(text), IP),
                GlobalErrorCodeConstants.UPLOAD_FILE_TYPE_INVALID);

        verify(faceLoginFailRedisDAO, times(3)).recordFail(IP, WINDOW);
    }

    @Test
    @DisplayName("计数：人脸服务不可用、超时(C 类)不计数")
    void faceLoginDoesNotCountServiceFailures() {
        when(faceService.recognize(any()))
                .thenThrow(new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE))
                .thenThrow(new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_TIMEOUT));

        assertServiceError(() -> service.faceLogin(List.of(jpeg()), IP),
                SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
        assertServiceError(() -> service.faceLogin(List.of(jpeg()), IP), SystemErrorCodeConstants.FACE_SERVICE_TIMEOUT);
        verify(faceLoginFailRedisDAO, never()).recordFail(anyString(), any());
    }

    @Test
    @DisplayName("核对用户：不存在 A0200、停用 A0202、ADMIN A0220，都不发会话并计数")
    void faceLoginChecksUser() {
        when(faceService.recognize(any())).thenReturn(binding());
        when(userRepository.findByIdAndDeletedFalse(USER_ID))
                .thenReturn(Optional.empty(), Optional.of(user(0)), Optional.of(user(1)));
        when(authenticationSupport.loadEnabledRoleCodes(USER_ID)).thenReturn(List.of(RoleCodeConstants.ADMIN));

        assertServiceError(() -> service.faceLogin(List.of(jpeg()), IP), SystemErrorCodeConstants.FACE_LOGIN_REJECTED);
        assertServiceError(() -> service.faceLogin(List.of(jpeg()), IP), SystemErrorCodeConstants.LOGIN_USER_DISABLED);
        assertServiceError(() -> service.faceLogin(List.of(jpeg()), IP),
                SystemErrorCodeConstants.FACE_LOGIN_ADMIN_FORBIDDEN);

        verify(authenticationSupport, never()).createSession(any());
        verify(faceLoginFailRedisDAO, times(3)).recordFail(eq(IP), any());
    }

    private static MultipartFile jpeg() {
        return new MockMultipartFile("image", "x.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
    }

    private static FaceBindingEntity binding() {
        FaceBindingEntity binding = new FaceBindingEntity();
        binding.setId(1L);
        binding.setUserId(USER_ID);
        binding.setPersonId("user-7");
        binding.setDeleted(false);
        return binding;
    }

    private static UserEntity user(int status) {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setUserNo("EMP007");
        user.setStatus(status);
        return user;
    }

    private static void assertServiceError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
