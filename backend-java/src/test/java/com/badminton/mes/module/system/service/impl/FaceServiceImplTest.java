package com.badminton.mes.module.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.badminton.mes.common.core.ErrorCode;
import com.badminton.mes.common.core.GlobalErrorCodeConstants;
import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.common.security.RoleCodeConstants;
import com.badminton.mes.module.system.client.InsightFaceApiException;
import com.badminton.mes.module.system.client.InsightFaceClient;
import com.badminton.mes.module.system.client.InsightFaceModels.DeleteFaceResult;
import com.badminton.mes.module.system.client.InsightFaceModels.EnrollResult;
import com.badminton.mes.module.system.client.InsightFaceModels.FaceSample;
import com.badminton.mes.module.system.client.FaceImage;
import com.badminton.mes.module.system.client.InsightFaceModels.RejectedImage;
import com.badminton.mes.module.system.client.InsightFaceModels.SearchMatch;
import com.badminton.mes.module.system.config.InsightFaceProperties;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;
import com.badminton.mes.module.system.controller.vo.FaceEnrollRespVO;
import com.badminton.mes.module.system.controller.vo.FaceProfileRespVO;
import com.badminton.mes.module.system.dal.entity.FaceBindingEntity;
import com.badminton.mes.module.system.dal.redis.FaceUserLockRedisDAO;
import com.badminton.mes.module.system.dal.repository.FaceBindingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** 人脸注册、状态查询、追加与删除样本、关闭、识别判定的业务规则。 */
@ExtendWith(MockitoExtension.class)
class FaceServiceImplTest {

    private static final Long USER_ID = 7L;

    private static final String PERSON_ID = "user-7";

    private static final String LOCK_TOKEN = "token";

    @Mock
    private InsightFaceClient insightFaceClient;

    @Mock
    private FaceBindingRepository faceBindingRepository;

    @Mock
    private FaceUserLockRedisDAO faceUserLockRedisDAO;

    private FaceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FaceServiceImpl(insightFaceClient, new InsightFaceProperties(),
                faceBindingRepository, faceUserLockRedisDAO);
        lenient().when(faceUserLockRedisDAO.tryLock(USER_ID)).thenReturn(Optional.of(LOCK_TOKEN));
        lenient().when(insightFaceClient.unexpected(anyString(), any()))
                .thenReturn(new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PMC", "WORKSHOP_MANAGER", "TEAM_LEADER", "OPERATOR", "INSPECTOR", "CRAFT_ENGINEER"})
    @DisplayName("注册：所有角色均可采集，包括 ADMIN 与混合角色")
    void enrollAllowsAllRoles(String role) {
        when(insightFaceClient.createPerson(eq(PERSON_ID), eq("7"), eq("张三"), anyList()))
                .thenReturn(new EnrollResult(List.of("f1", "f2"), List.of()));

        FaceEnrollRespVO response = service.enroll(user(role, RoleCodeConstants.OPERATOR), images(2), true);

        assertThat(response.getAcceptedCount()).isEqualTo(2);
        verify(faceBindingRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("注册：未勾选同意、张数不符返回 A0400，伪装成 jpg 的文本返回 A0701")
    void enrollValidatesInput() {
        assertServiceError(() -> service.enroll(user(), images(2), false),
                SystemErrorCodeConstants.FACE_CONSENT_REQUIRED);
        assertServiceError(() -> service.enroll(user(), images(1), true),
                SystemErrorCodeConstants.FACE_IMAGE_COUNT_INVALID);
        assertServiceError(() -> service.enroll(user(), images(4), true),
                SystemErrorCodeConstants.FACE_IMAGE_COUNT_INVALID);
        MultipartFile text = new MockMultipartFile("images", "a.jpg", "image/jpeg",
                "not an image".getBytes(StandardCharsets.UTF_8));
        assertServiceError(() -> service.enroll(user(), List.of(images(1).getFirst(), text), true),
                GlobalErrorCodeConstants.UPLOAD_FILE_TYPE_INVALID);
        verifyNoInteractions(insightFaceClient, faceUserLockRedisDAO);
    }

    @Test
    @DisplayName("注册：锁被占用返回 A0503")
    void enrollRejectsWhenLocked() {
        when(faceUserLockRedisDAO.tryLock(USER_ID)).thenReturn(Optional.empty());

        assertServiceError(() -> service.enroll(user(), images(2), true),
                SystemErrorCodeConstants.FACE_OPERATION_IN_PROGRESS);
        verifyNoInteractions(insightFaceClient);
    }

    @Test
    @DisplayName("注册：已有有效绑定返回 A0111，并释放锁")
    void enrollRejectsDuplicate() {
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding()));

        assertServiceError(() -> service.enroll(user(), images(2), true),
                SystemErrorCodeConstants.FACE_ALREADY_ENROLLED);
        verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
        verifyNoInteractions(insightFaceClient);
    }

    @Test
    @DisplayName("注册：至少一张成功时写绑定，Person id 为 user-{userId}，记录同意时间")
    void enrollCreatesBinding() {
        when(insightFaceClient.createPerson(eq(PERSON_ID), eq("7"), eq("张三"), anyList()))
                .thenReturn(new EnrollResult(List.of("f1", "f2"), List.of(new RejectedImage(3, "未检测到人脸"))));

        FaceEnrollRespVO response = service.enroll(user(), images(3), true);

        assertThat(response.getAcceptedCount()).isEqualTo(2);
        assertThat(response.getFaceCount()).isEqualTo(2);
        assertThat(response.getRejectedImages()).singleElement().satisfies(rejected -> {
            assertThat(rejected.getIndex()).isEqualTo(3);
            assertThat(rejected.getReason()).isEqualTo("未检测到人脸");
        });
        ArgumentCaptor<FaceBindingEntity> saved = ArgumentCaptor.forClass(FaceBindingEntity.class);
        verify(faceBindingRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getPersonId()).isEqualTo(PERSON_ID);
        assertThat(saved.getValue().getCollectionId()).isEqualTo("course_users");
        assertThat(saved.getValue().getConsentTime()).isNotNull();
        assertThat(saved.getValue().getDeleted()).isFalse();
        verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
    }

    @Test
    @DisplayName("注册：全部照片被拒仍返回成功，acceptedCount=0，不写绑定")
    void enrollAllRejected() {
        when(insightFaceClient.createPerson(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new EnrollResult(List.of(),
                        List.of(new RejectedImage(1, "a"), new RejectedImage(2, "b"))));

        FaceEnrollRespVO response = service.enroll(user(), images(2), true);

        assertThat(response.getAcceptedCount()).isZero();
        assertThat(response.getFaceCount()).isZero();
        assertThat(response.getRejectedImages()).hasSize(2);
        verify(faceBindingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("注册：409 person_exists 时删除残留后重建一次")
    void enrollReplacesLeftoverPerson() {
        when(insightFaceClient.createPerson(anyString(), anyString(), anyString(), anyList()))
                .thenThrow(conflict())
                .thenReturn(new EnrollResult(List.of("f1"), List.of()));

        FaceEnrollRespVO response = service.enroll(user(), images(2), true);

        assertThat(response.getAcceptedCount()).isEqualTo(1);
        InOrder order = inOrder(insightFaceClient, faceBindingRepository);
        order.verify(insightFaceClient).createPerson(anyString(), anyString(), anyString(), anyList());
        order.verify(insightFaceClient).deletePerson(PERSON_ID);
        order.verify(insightFaceClient).createPerson(anyString(), anyString(), anyString(), anyList());
        order.verify(faceBindingRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("注册：重建后仍然 409 返回 C0001")
    void enrollFailsWhenConflictPersists() {
        when(insightFaceClient.createPerson(anyString(), anyString(), anyString(), anyList()))
                .thenThrow(conflict(), conflict());

        assertServiceError(() -> service.enroll(user(), images(2), true),
                SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
        verify(insightFaceClient, times(2)).createPerson(anyString(), anyString(), anyString(), anyList());
        verify(faceBindingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("注册：写绑定失败时立即删除刚创建的 Person")
    void enrollRollsBackPersonWhenBindingFails() {
        when(insightFaceClient.createPerson(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new EnrollResult(List.of("f1"), List.of()));
        when(faceBindingRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertServiceError(() -> service.enroll(user(), images(2), true),
                SystemErrorCodeConstants.FACE_ALREADY_ENROLLED);
        verify(insightFaceClient).deletePerson(PERSON_ID);
        verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PMC", "WORKSHOP_MANAGER", "TEAM_LEADER", "OPERATOR", "INSPECTOR", "CRAFT_ENGINEER"})
    @DisplayName("查询：所有角色的未注册账号均允许进入采集，不调用人脸服务")
    void profileNotEnrolled(String role) {
        FaceProfileRespVO response = service.getProfile(user(role));

        assertThat(response.getEnrolled()).isFalse();
        assertThat(response.getFaceLoginAllowed()).isTrue();
        assertThat(response.getFaceCount()).isZero();
        assertThat(response.getMaxFaces()).isEqualTo(5);
        assertThat(response.getSamples()).isEmpty();
        verifyNoInteractions(insightFaceClient);
    }

    @Test
    @DisplayName("查询：已注册时返回样本列表")
    void profileEnrolled() {
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding()));
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 9, 10, 0);
        when(insightFaceClient.listFaces(PERSON_ID)).thenReturn(Optional.of(List.of(new FaceSample("f1", createdAt))));

        FaceProfileRespVO response = service.getProfile(user());

        assertThat(response.getEnrolled()).isTrue();
        assertThat(response.getFaceLoginAllowed()).isTrue();
        assertThat(response.getFaceCount()).isEqualTo(1);
        assertThat(response.getSamples()).singleElement().satisfies(sample -> {
            assertThat(sample.getFaceId()).isEqualTo("f1");
            assertThat(sample.getCreatedAt()).isEqualTo(createdAt);
        });
    }

    @Test
    @DisplayName("查询：绑定存在但 Person 已不存在时自愈为未注册")
    void profileHealsMissingPerson() {
        FaceBindingEntity binding = binding();
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding));
        when(insightFaceClient.listFaces(PERSON_ID)).thenReturn(Optional.empty());

        FaceProfileRespVO response = service.getProfile(user());

        assertThat(response.getEnrolled()).isFalse();
        assertThat(binding.getDeleted()).isTrue();
        verify(faceBindingRepository).saveAndFlush(binding);
    }

    @Test
    @DisplayName("查询：Person 没有有效样本时回到未采集状态")
    void profileHealsEmptyPerson() {
        FaceBindingEntity binding = binding();
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding));
        when(insightFaceClient.listFaces(PERSON_ID)).thenReturn(Optional.of(List.of()));

        FaceProfileRespVO response = service.getProfile(user(RoleCodeConstants.ADMIN));

        assertThat(response.getEnrolled()).isFalse();
        assertThat(response.getFaceLoginAllowed()).isTrue();
        assertThat(response.getFaceCount()).isZero();
        assertThat(response.getSamples()).isEmpty();
        assertThat(binding.getDeleted()).isTrue();
        verify(faceBindingRepository).saveAndFlush(binding);
    }

    @Test
    @DisplayName("重采集：在同一用户锁内删除旧特征、关闭旧绑定并记录本次同意和新样本")
    void reenrollReplacesExistingEnrollment() {
        FaceBindingEntity old = binding();
        old.setConsentTime(LocalDateTime.of(2026, 11, 9, 10, 0));
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID))
                .thenReturn(Optional.of(old), Optional.empty());
        when(insightFaceClient.createPerson(eq(PERSON_ID), eq("7"), eq("张三"), anyList()))
                .thenReturn(new EnrollResult(List.of("new1", "new2"), List.of(new RejectedImage(3, "照片模糊"))));

        FaceEnrollRespVO response = service.reenroll(user(RoleCodeConstants.ADMIN), images(3), true);

        assertThat(response.getAcceptedCount()).isEqualTo(2);
        assertThat(response.getFaceCount()).isEqualTo(2);
        assertThat(response.getRejectedImages()).singleElement().satisfies(rejected -> {
            assertThat(rejected.getIndex()).isEqualTo(3);
            assertThat(rejected.getReason()).isEqualTo("照片模糊");
        });
        InOrder order = inOrder(faceUserLockRedisDAO, insightFaceClient, faceBindingRepository);
        order.verify(faceUserLockRedisDAO).tryLock(USER_ID);
        order.verify(insightFaceClient).deletePerson(PERSON_ID);
        order.verify(faceBindingRepository).saveAndFlush(old);
        order.verify(insightFaceClient).createPerson(eq(PERSON_ID), eq("7"), eq("张三"), anyList());
        ArgumentCaptor<FaceBindingEntity> saved = ArgumentCaptor.forClass(FaceBindingEntity.class);
        order.verify(faceBindingRepository).saveAndFlush(saved.capture());
        order.verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
        assertThat(old.getDeleted()).isTrue();
        assertThat(saved.getValue()).isNotSameAs(old);
        assertThat(saved.getValue().getConsentTime()).isAfter(old.getConsentTime());
        assertThat(saved.getValue().getDeleted()).isFalse();
        assertThat(saved.getValue().getLastLoginTime()).isNull();
        verify(faceUserLockRedisDAO, times(1)).tryLock(USER_ID);
        verify(insightFaceClient, never()).addFaces(anyString(), anyList());
    }

    @Test
    @DisplayName("重采集：未同意、张数或格式错误时保留旧特征，不加锁")
    void reenrollValidatesBeforeDeleting() {
        assertServiceError(() -> service.reenroll(user(), images(2), null),
                SystemErrorCodeConstants.FACE_CONSENT_REQUIRED);
        assertServiceError(() -> service.reenroll(user(), images(2), false),
                SystemErrorCodeConstants.FACE_CONSENT_REQUIRED);
        assertServiceError(() -> service.reenroll(user(), null, true),
                SystemErrorCodeConstants.FACE_IMAGE_COUNT_INVALID);
        assertServiceError(() -> service.reenroll(user(), images(1), true),
                SystemErrorCodeConstants.FACE_IMAGE_COUNT_INVALID);
        assertServiceError(() -> service.reenroll(user(), images(4), true),
                SystemErrorCodeConstants.FACE_IMAGE_COUNT_INVALID);
        MultipartFile text = new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[] {0});
        assertServiceError(() -> service.reenroll(user(), List.of(text, text), true),
                GlobalErrorCodeConstants.UPLOAD_FILE_TYPE_INVALID);
        verifyNoInteractions(insightFaceClient, faceBindingRepository, faceUserLockRedisDAO);
    }

    @Test
    @DisplayName("重采集：占锁时返回 A0503，保留旧特征")
    void reenrollRejectsWhenLocked() {
        when(faceUserLockRedisDAO.tryLock(USER_ID)).thenReturn(Optional.empty());

        assertServiceError(() -> service.reenroll(user(), images(2), true),
                SystemErrorCodeConstants.FACE_OPERATION_IN_PROGRESS);

        verifyNoInteractions(insightFaceClient, faceBindingRepository);
        verify(faceUserLockRedisDAO, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("重采集：删除旧 Person 失败时保留旧绑定并释放锁")
    void reenrollKeepsBindingWhenDeleteFails() {
        when(insightFaceClient.deletePerson(PERSON_ID))
                .thenThrow(new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE));

        assertServiceError(() -> service.reenroll(user(), images(2), true),
                SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);

        verifyNoInteractions(faceBindingRepository);
        verify(insightFaceClient, never()).createPerson(anyString(), anyString(), anyString(), anyList());
        verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
    }

    @Test
    @DisplayName("重采集：新照片全部拒收时清除旧绑定，不写新绑定，查询返回未采集")
    void reenrollAllRejected() {
        FaceBindingEntity old = binding();
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID))
                .thenReturn(Optional.of(old), Optional.empty());
        when(insightFaceClient.createPerson(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new EnrollResult(List.of(), List.of(new RejectedImage(1, "未检测到人脸"))));

        FaceEnrollRespVO response = service.reenroll(user(), images(2), true);

        assertThat(response.getAcceptedCount()).isZero();
        assertThat(response.getFaceCount()).isZero();
        assertThat(old.getDeleted()).isTrue();
        verify(faceBindingRepository).saveAndFlush(old);
        verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
        assertThat(service.getProfile(user()).getEnrolled()).isFalse();
    }

    @Test
    @DisplayName("重采集：旧特征删除后新建失败，旧绑定保持关闭，可重新提交")
    void reenrollCreationFailureKeepsOldBindingClosed() {
        FaceBindingEntity old = binding();
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID))
                .thenReturn(Optional.of(old), Optional.empty());
        when(insightFaceClient.createPerson(anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_TIMEOUT));

        assertServiceError(() -> service.reenroll(user(), images(2), true),
                SystemErrorCodeConstants.FACE_SERVICE_TIMEOUT);

        assertThat(old.getDeleted()).isTrue();
        verify(faceBindingRepository).saveAndFlush(old);
        verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
        assertThat(service.getProfile(user()).getEnrolled()).isFalse();
    }

    @Test
    @DisplayName("重采集：未注册用户可以直接采集")
    void reenrollWithoutExistingBinding() {
        when(insightFaceClient.createPerson(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new EnrollResult(List.of("f1", "f2", "f3"), List.of()));

        FaceEnrollRespVO response = service.reenroll(user(), images(3), true);

        assertThat(response.getFaceCount()).isEqualTo(3);
        verify(faceBindingRepository).saveAndFlush(any());
        verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
    }

    @Test
    @DisplayName("关闭：先删 Person 再把绑定标为已删除")
    void removeEnrollmentDeletesPersonThenBinding() {
        FaceBindingEntity binding = binding();
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding));

        service.removeEnrollment(USER_ID);

        InOrder order = inOrder(insightFaceClient, faceBindingRepository);
        order.verify(insightFaceClient).deletePerson(PERSON_ID);
        order.verify(faceBindingRepository).saveAndFlush(binding);
        assertThat(binding.getDeleted()).isTrue();
    }

    @Test
    @DisplayName("关闭：删除 Person 失败时绑定保持不变")
    void removeEnrollmentKeepsBindingWhenServiceDown() {
        when(insightFaceClient.deletePerson(PERSON_ID))
                .thenThrow(new ServiceException(SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE));

        assertServiceError(() -> service.removeEnrollment(USER_ID),
                SystemErrorCodeConstants.FACE_SERVICE_UNAVAILABLE);
        verify(faceBindingRepository, never()).saveAndFlush(any());
        verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
    }

    @Test
    @DisplayName("识别：唯一候选高于阈值且绑定一致时通过，只有一个候选不做分差校验")
    void recognizeSingleCandidate() {
        when(insightFaceClient.search(any())).thenReturn(List.of(new SearchMatch(PERSON_ID, "7", 0.41)));
        FaceBindingEntity binding = binding();
        when(faceBindingRepository.findByCollectionIdAndPersonIdAndDeletedFalse("course_users", PERSON_ID))
                .thenReturn(Optional.of(binding));

        assertThat(service.recognize(loginImage())).isSameAs(binding);
    }

    @Test
    @DisplayName("识别：同一人的多条记录去重后不会因分差为 0 拒绝本人")
    void recognizeDeduplicatesSamePerson() {
        when(insightFaceClient.search(any())).thenReturn(List.of(
                new SearchMatch(PERSON_ID, "7", 0.70), new SearchMatch(PERSON_ID, "7", 0.69),
                new SearchMatch("user-8", "8", 0.30)));
        when(faceBindingRepository.findByCollectionIdAndPersonIdAndDeletedFalse("course_users", PERSON_ID))
                .thenReturn(Optional.of(binding()));

        assertThat(service.recognize(loginImage()).getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("识别：没有候选、第一名低于阈值、两人分差小于 margin 都返回 A0200")
    void recognizeRejects() {
        when(insightFaceClient.search(any())).thenReturn(
                List.of(),
                List.of(new SearchMatch(PERSON_ID, "7", 0.39)),
                List.of(new SearchMatch(PERSON_ID, "7", 0.60), new SearchMatch("user-8", "8", 0.56)));

        for (int i = 0; i < 3; i++) {
            assertServiceError(() -> service.recognize(loginImage()), SystemErrorCodeConstants.FACE_LOGIN_REJECTED);
        }
        verify(faceBindingRepository, never()).findByCollectionIdAndPersonIdAndDeletedFalse(any(), any());
    }

    @Test
    @DisplayName("识别：Person 没有有效绑定或 external_id 不一致返回 A0200")
    void recognizeRejectsBindingMismatch() {
        when(insightFaceClient.search(any())).thenReturn(
                List.of(new SearchMatch(PERSON_ID, "7", 0.80)), List.of(new SearchMatch(PERSON_ID, "99", 0.80)));
        when(faceBindingRepository.findByCollectionIdAndPersonIdAndDeletedFalse("course_users", PERSON_ID))
                .thenReturn(Optional.empty(), Optional.of(binding()));

        assertServiceError(() -> service.recognize(loginImage()), SystemErrorCodeConstants.FACE_LOGIN_REJECTED);
        assertServiceError(() -> service.recognize(loginImage()), SystemErrorCodeConstants.FACE_LOGIN_REJECTED);
    }

    @Test
    @DisplayName("识别：照片无人脸或无法解码(422)返回 A0700")
    void recognizeImageRejected() {
        when(insightFaceClient.search(any())).thenThrow(new InsightFaceApiException(422, "face_not_found", null));

        assertServiceError(() -> service.recognize(loginImage()), SystemErrorCodeConstants.FACE_IMAGE_REJECTED);
    }

    @Test
    @DisplayName("记录登录时间：只更新有效绑定，不保存包含旧删除状态的实体")
    void recordLoginDoesNotOverwriteConcurrentDisable() {
        FaceBindingEntity staleBinding = binding();
        when(faceBindingRepository.updateLastLoginTimeIfActive(eq(staleBinding.getId()), any())).thenReturn(0);

        service.recordLogin(staleBinding);

        verify(faceBindingRepository).updateLastLoginTimeIfActive(eq(staleBinding.getId()), any(LocalDateTime.class));
        verify(faceBindingRepository, never()).save(any());
        verify(faceBindingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("记录登录时间：写库失败不抛异常")
    void recordLoginSwallowsFailure() {
        FaceBindingEntity binding = binding();
        when(faceBindingRepository.updateLastLoginTimeIfActive(eq(binding.getId()), any()))
                .thenThrow(new RuntimeException("db down"));

        service.recordLogin(binding);

        verify(faceBindingRepository).updateLastLoginTimeIfActive(eq(binding.getId()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("追加：所有角色未注册时均返回 A0440")
    void addSamplesRejectsNotEnrolled() {
        assertServiceError(() -> service.addSamples(user(RoleCodeConstants.ADMIN), images(1)),
                SystemErrorCodeConstants.FACE_NOT_ENROLLED);
        assertServiceError(() -> service.addSamples(user(), images(1)), SystemErrorCodeConstants.FACE_NOT_ENROLLED);
        verify(insightFaceClient, never()).addFaces(anyString(), anyList());
    }

    @Test
    @DisplayName("追加：锁内预校验上限，当前 4 张再加 2 张返回 A0425，不调用追加接口")
    void addSamplesChecksLimitInsideLock() {
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding()));
        when(insightFaceClient.listFaces(PERSON_ID)).thenReturn(Optional.of(samples(4)));

        assertServiceError(() -> service.addSamples(user(), images(2)), SystemErrorCodeConstants.FACE_SAMPLE_LIMIT);
        verify(insightFaceClient, never()).addFaces(anyString(), anyList());
        verify(faceUserLockRedisDAO).unlock(USER_ID, LOCK_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PMC", "WORKSHOP_MANAGER", "TEAM_LEADER", "OPERATOR", "INSPECTOR", "CRAFT_ENGINEER"})
    @DisplayName("追加：所有角色均可追加，faceCount 为原有加本次成功张数")
    void addSamplesSuccess(String role) {
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding()));
        when(insightFaceClient.listFaces(PERSON_ID)).thenReturn(Optional.of(samples(2)));
        when(insightFaceClient.addFaces(eq(PERSON_ID), anyList()))
                .thenReturn(new EnrollResult(List.of("f9"), List.of(new RejectedImage(2, "未检测到人脸"))));

        FaceEnrollRespVO response = service.addSamples(user(role), images(2));

        assertThat(response.getAcceptedCount()).isEqualTo(1);
        assertThat(response.getFaceCount()).isEqualTo(3);
        assertThat(response.getRejectedImages()).hasSize(1);
    }

    @Test
    @DisplayName("追加：Person 已不存在时自愈并返回 A0440；InsightFace 上限 409 兜底为 A0425")
    void addSamplesHandlesInsightFaceErrors() {
        FaceBindingEntity binding = binding();
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding));
        when(insightFaceClient.listFaces(PERSON_ID)).thenReturn(Optional.of(samples(1)));
        when(insightFaceClient.addFaces(eq(PERSON_ID), anyList()))
                .thenThrow(new InsightFaceApiException(409, "person_face_limit_exceeded", null))
                .thenThrow(new InsightFaceApiException(404, "person_not_found", null));

        assertServiceError(() -> service.addSamples(user(), images(1)), SystemErrorCodeConstants.FACE_SAMPLE_LIMIT);
        assertThat(binding.getDeleted()).isFalse();
        assertServiceError(() -> service.addSamples(user(), images(1)), SystemErrorCodeConstants.FACE_NOT_ENROLLED);
        assertThat(binding.getDeleted()).isTrue();
    }

    @Test
    @DisplayName("删除样本：未注册 A0440，样本不存在 A0402，Person 不存在时自愈并返回 A0440")
    void deleteSampleErrors() {
        assertServiceError(() -> service.deleteSample(user(), "f1"), SystemErrorCodeConstants.FACE_NOT_ENROLLED);

        FaceBindingEntity binding = binding();
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding));
        when(insightFaceClient.deleteFace(PERSON_ID, "f1"))
                .thenReturn(DeleteFaceResult.FACE_NOT_FOUND, DeleteFaceResult.PERSON_NOT_FOUND);

        assertServiceError(() -> service.deleteSample(user(), "f1"), SystemErrorCodeConstants.FACE_SAMPLE_NOT_FOUND);
        assertThat(binding.getDeleted()).isFalse();
        assertServiceError(() -> service.deleteSample(user(), "f1"), SystemErrorCodeConstants.FACE_NOT_ENROLLED);
        assertThat(binding.getDeleted()).isTrue();
    }

    @Test
    @DisplayName("删除样本：还有剩余时保留绑定；删到 0 张时删除 Person 并关闭绑定")
    void deleteSampleClosesWhenEmpty() {
        FaceBindingEntity binding = binding();
        when(faceBindingRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(binding));
        when(insightFaceClient.deleteFace(eq(PERSON_ID), anyString())).thenReturn(DeleteFaceResult.DELETED);
        when(insightFaceClient.listFaces(PERSON_ID)).thenReturn(Optional.of(samples(1)), Optional.of(List.of()));

        service.deleteSample(user(), "f1");
        assertThat(binding.getDeleted()).isFalse();
        verify(insightFaceClient, never()).deletePerson(anyString());

        service.deleteSample(user(), "f2");
        verify(insightFaceClient).deletePerson(PERSON_ID);
        assertThat(binding.getDeleted()).isTrue();
        verify(faceUserLockRedisDAO, times(2)).unlock(USER_ID, LOCK_TOKEN);
    }

    private static List<FaceSample> samples(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new FaceSample("f" + i, LocalDateTime.of(2026, 11, 9, 10, i)))
                .toList();
    }

    private static FaceImage loginImage() {
        return new FaceImage("photo-1.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, MediaType.IMAGE_JPEG);
    }

    private static LoginUser user(String... roleCodes) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(USER_ID);
        loginUser.setUserName("张三");
        loginUser.setRoleCodes(roleCodes.length == 0 ? List.of("OPERATOR") : List.of(roleCodes));
        return loginUser;
    }

    private static List<MultipartFile> images(int count) {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        return java.util.stream.IntStream.range(0, count)
                .<MultipartFile>mapToObj(i -> new MockMultipartFile("images", "x.jpg", "image/jpeg", jpeg))
                .toList();
    }

    private static FaceBindingEntity binding() {
        FaceBindingEntity binding = new FaceBindingEntity();
        binding.setId(1L);
        binding.setUserId(USER_ID);
        binding.setPersonId(PERSON_ID);
        binding.setDeleted(false);
        return binding;
    }

    private static InsightFaceApiException conflict() {
        return new InsightFaceApiException(409, "person_exists", null);
    }

    private static void assertServiceError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
