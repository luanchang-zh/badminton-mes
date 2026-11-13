package com.badminton.mes.module.system.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.module.system.client.InsightFaceClient;
import com.badminton.mes.module.system.client.InsightFaceModels.EnrollResult;
import com.badminton.mes.module.system.client.InsightFaceModels.FaceSample;
import com.badminton.mes.module.system.client.InsightFaceModels.RejectedImage;
import com.badminton.mes.module.system.client.InsightFaceModels.SearchMatch;
import com.badminton.mes.module.system.config.InsightFaceProperties;
import com.badminton.mes.module.system.controller.vo.AuthLoginReqVO;
import com.badminton.mes.module.system.controller.vo.AuthLoginRespVO;
import com.badminton.mes.module.system.controller.vo.FaceEnrollRespVO;
import com.badminton.mes.module.system.controller.vo.FaceProfileRespVO;
import com.badminton.mes.module.system.dal.entity.FaceBindingEntity;
import com.badminton.mes.module.system.dal.entity.RoleEntity;
import com.badminton.mes.module.system.dal.entity.UserEntity;
import com.badminton.mes.module.system.dal.entity.UserRoleEntity;
import com.badminton.mes.module.system.dal.redis.FaceLoginFailRedisDAO;
import com.badminton.mes.module.system.dal.redis.FaceUserLockRedisDAO;
import com.badminton.mes.module.system.dal.redis.LoginSessionRedisDAO;
import com.badminton.mes.module.system.dal.repository.FaceBindingRepository;
import com.badminton.mes.module.system.dal.repository.RoleRepository;
import com.badminton.mes.module.system.dal.repository.UserRepository;
import com.badminton.mes.module.system.dal.repository.UserRoleRepository;
import com.badminton.mes.module.system.service.AuthenticationSupport;
import com.badminton.mes.module.system.service.RoleService;
import com.badminton.mes.module.system.service.WechatUserBindingService;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 真实认证、人脸业务服务协作；仅替换数据库、Redis 与 InsightFace 等外部依赖。 */
@ExtendWith(MockitoExtension.class)
class FaceSettingsWorkflowTest {

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PMC", "WORKSHOP_MANAGER", "TEAM_LEADER", "OPERATOR", "INSPECTOR", "CRAFT_ENGINEER"})
    void passwordSessionCanEnrollReenrollAndLoginWithFace(String roleCode) {
        UserRepository users = mock(UserRepository.class);
        UserRoleRepository userRoles = mock(UserRoleRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        LoginSessionRedisDAO sessions = mock(LoginSessionRedisDAO.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        InsightFaceClient client = mock(InsightFaceClient.class);
        FaceBindingRepository bindings = mock(FaceBindingRepository.class);
        FaceUserLockRedisDAO locks = mock(FaceUserLockRedisDAO.class);
        InsightFaceProperties properties = new InsightFaceProperties();
        FaceServiceImpl faces = new FaceServiceImpl(client, properties, bindings, locks);
        AuthenticationSupport authentication = new AuthenticationSupport(users, userRoles, roles, sessions, passwords);
        AuthServiceImpl auth = new AuthServiceImpl(users, userRoles, roles, sessions, passwords, authentication,
                mock(RoleService.class), mock(WechatUserBindingService.class), faces,
                mock(FaceLoginFailRedisDAO.class), properties);

        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUserNo("settings-test");
        user.setUserName("人脸设置测试");
        user.setPassword("encoded-password");
        user.setStatus(1);
        when(users.findByUserNoAndDeletedFalse("settings-test")).thenReturn(Optional.of(user));
        when(users.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(user));
        when(passwords.matches("test-password", "encoded-password")).thenReturn(true);
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setRoleId(1L);
        when(userRoles.findByUserIdAndDeletedFalse(7L)).thenReturn(List.of(userRole));
        RoleEntity role = new RoleEntity();
        role.setRoleCode(roleCode);
        role.setStatus(1);
        when(roles.findByIdInAndDeletedFalseOrderByIdAsc(List.of(1L))).thenReturn(List.of(role));
        when(locks.tryLock(7L)).thenReturn(Optional.of("lock-token"));

        AtomicReference<FaceBindingEntity> current = new AtomicReference<>();
        when(bindings.findByUserIdAndDeletedFalse(7L)).thenAnswer(invocation ->
                Optional.ofNullable(current.get()).filter(binding -> !binding.getDeleted()));
        when(bindings.saveAndFlush(any())).thenAnswer(invocation -> {
            FaceBindingEntity binding = invocation.getArgument(0);
            binding.setId(binding.getId() == null ? 1L : binding.getId());
            current.set(binding);
            return binding;
        });
        when(client.createPerson(anyString(), anyString(), anyString(), anyList())).thenReturn(
                new EnrollResult(List.of("old1", "old2", "old3"), List.of()),
                new EnrollResult(List.of("new1", "new2"), List.of(new RejectedImage(3, "照片模糊"))));
        LocalDateTime capturedAt = LocalDateTime.of(2026, 11, 12, 10, 0);
        when(client.listFaces("user-7")).thenReturn(
                Optional.of(List.of(new FaceSample("old1", capturedAt), new FaceSample("old2", capturedAt),
                        new FaceSample("old3", capturedAt))),
                Optional.of(List.of(new FaceSample("new1", capturedAt), new FaceSample("new2", capturedAt))));

        AuthLoginReqVO request = new AuthLoginReqVO();
        request.setUserNo("settings-test");
        request.setPassword("test-password");
        AuthLoginRespVO passwordLogin = auth.login(request);
        ArgumentCaptor<LoginUser> sessionUser = ArgumentCaptor.forClass(LoginUser.class);
        verify(sessions).createSession(anyString(), sessionUser.capture());
        LoginUser loggedIn = sessionUser.getValue();
        assertThat(loggedIn.getRoleCodes()).containsExactly(roleCode);
        FaceProfileRespVO initial = faces.getProfile(loggedIn);
        assertThat(initial.getFaceLoginAllowed()).isTrue();
        assertThat(initial.getEnrolled()).isFalse();

        FaceEnrollRespVO enrolled = faces.enroll(loggedIn, images(), true);
        assertThat(enrolled.getFaceCount()).isEqualTo(3);
        FaceProfileRespVO firstProfile = faces.getProfile(loggedIn);
        assertThat(firstProfile.getEnrolled()).isTrue();
        assertThat(firstProfile.getFaceCount()).isEqualTo(3);
        FaceBindingEntity oldBinding = current.get();

        FaceEnrollRespVO replaced = faces.reenroll(loggedIn, images(), true);
        assertThat(replaced.getAcceptedCount()).isEqualTo(2);
        assertThat(replaced.getFaceCount()).isEqualTo(2);
        assertThat(replaced.getRejectedImages()).singleElement().satisfies(rejected ->
                assertThat(rejected.getIndex()).isEqualTo(3));
        assertThat(oldBinding.getDeleted()).isTrue();
        assertThat(current.get()).isNotSameAs(oldBinding);
        FaceProfileRespVO updated = faces.getProfile(loggedIn);
        assertThat(updated.getEnrolled()).isTrue();
        assertThat(updated.getFaceLoginAllowed()).isTrue();
        assertThat(updated.getSamples()).extracting(FaceProfileRespVO.Sample::getFaceId)
                .containsExactly("new1", "new2");
        verify(client).deletePerson("user-7");
        verify(locks, times(2)).tryLock(7L);
        verify(locks, times(2)).unlock(7L, "lock-token");

        when(client.search(any())).thenReturn(List.of(new SearchMatch("user-7", "7", 0.8)));
        when(bindings.findByCollectionIdAndPersonIdAndDeletedFalse("course_users", "user-7"))
                .thenReturn(Optional.of(current.get()));
        AuthLoginRespVO faceLogin = auth.faceLogin(List.of(images().getFirst()), "127.0.0.1");
        assertThat(faceLogin.getToken()).isNotBlank().isNotEqualTo(passwordLogin.getToken());
        assertThat(faceLogin.getUserId()).isEqualTo(passwordLogin.getUserId());
        assertThat(faceLogin.getRoleCodes()).containsExactly(roleCode);
    }

    private static List<MultipartFile> images() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0};
        return java.util.stream.IntStream.range(0, 3)
                .<MultipartFile>mapToObj(index -> new MockMultipartFile("images", "photo.jpg", "image/jpeg", jpeg))
                .toList();
    }
}
