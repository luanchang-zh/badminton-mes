package com.badminton.mes.module.system.service.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.common.security.RoleCodeConstants;
import com.badminton.mes.module.system.client.FaceImage;
import com.badminton.mes.module.system.client.InsightFaceApiException;
import com.badminton.mes.module.system.client.InsightFaceClient;
import com.badminton.mes.module.system.client.InsightFaceModels.EnrollResult;
import com.badminton.mes.module.system.client.InsightFaceModels.FaceSample;
import com.badminton.mes.module.system.client.InsightFaceModels.RejectedImage;
import com.badminton.mes.module.system.client.InsightFaceModels.SearchMatch;
import com.badminton.mes.module.system.config.InsightFaceProperties;
import com.badminton.mes.module.system.constants.SystemErrorCodeConstants;
import com.badminton.mes.module.system.controller.vo.FaceEnrollRespVO;
import com.badminton.mes.module.system.controller.vo.FaceProfileRespVO;
import com.badminton.mes.module.system.dal.entity.FaceBindingEntity;
import com.badminton.mes.module.system.dal.redis.FaceUserLockRedisDAO;
import com.badminton.mes.module.system.dal.repository.FaceBindingRepository;
import com.badminton.mes.module.system.service.FaceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸登录注册、识别与样本管理服务实现，处理顺序见规划第 4.3～4.8 节。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
@Service
public class FaceServiceImpl implements FaceService {

    private static final Logger logger = LoggerFactory.getLogger(FaceServiceImpl.class);

    /** 首次注册最少照片张数 */
    private static final int ENROLL_MIN_IMAGES = 2;

    /** 首次注册最多照片张数 */
    private static final int ENROLL_MAX_IMAGES = 3;

    private static final String PERSON_ID_PREFIX = "user-";

    private final InsightFaceClient insightFaceClient;

    private final InsightFaceProperties properties;

    private final FaceBindingRepository faceBindingRepository;

    private final FaceUserLockRedisDAO faceUserLockRedisDAO;

    /**
     * 构造人脸服务。
     *
     * @param insightFaceClient InsightFace 客户端
     * @param properties 人脸服务配置
     * @param faceBindingRepository 绑定 Repository
     * @param faceUserLockRedisDAO 单用户锁
     */
    public FaceServiceImpl(InsightFaceClient insightFaceClient, InsightFaceProperties properties,
                           FaceBindingRepository faceBindingRepository,
                           FaceUserLockRedisDAO faceUserLockRedisDAO) {
        this.insightFaceClient = insightFaceClient;
        this.properties = properties;
        this.faceBindingRepository = faceBindingRepository;
        this.faceUserLockRedisDAO = faceUserLockRedisDAO;
    }

    @Override
    public FaceEnrollRespVO enroll(LoginUser loginUser, List<MultipartFile> images, Boolean consent) {
        if (isAdmin(loginUser)) {
            throw new ServiceException(SystemErrorCodeConstants.FACE_ENROLL_ADMIN_FORBIDDEN);
        }
        if (!Boolean.TRUE.equals(consent)) {
            throw new ServiceException(SystemErrorCodeConstants.FACE_CONSENT_REQUIRED);
        }
        if (images == null || images.size() < ENROLL_MIN_IMAGES || images.size() > ENROLL_MAX_IMAGES) {
            throw new ServiceException(SystemErrorCodeConstants.FACE_IMAGE_COUNT_INVALID,
                    "首次注册需要 " + ENROLL_MIN_IMAGES + "～" + ENROLL_MAX_IMAGES + " 张照片");
        }
        List<FaceImage> faceImages = FaceImage.fromUploads(images);
        Long userId = loginUser.getUserId();
        LocalDateTime consentTime = LocalDateTime.now();
        return withUserLock(userId, () -> doEnroll(loginUser, faceImages, consentTime));
    }

    @Override
    public FaceProfileRespVO getProfile(LoginUser loginUser) {
        FaceProfileRespVO response = new FaceProfileRespVO();
        response.setFaceLoginAllowed(!isAdmin(loginUser));
        response.setMaxFaces(properties.getMaxFacesPerPerson());
        response.setEnrolled(false);
        response.setFaceCount(0);
        response.setSamples(List.of());

        Optional<FaceBindingEntity> binding = faceBindingRepository.findByUserIdAndDeletedFalse(loginUser.getUserId());
        if (binding.isEmpty()) {
            return response;
        }
        Optional<List<FaceSample>> samples = insightFaceClient.listFaces(binding.get().getPersonId());
        if (samples.isEmpty()) {
            healBinding(binding.get());
            return response;
        }
        response.setEnrolled(true);
        response.setFaceCount(samples.get().size());
        response.setSamples(samples.get().stream().map(FaceServiceImpl::toSampleVO).toList());
        return response;
    }

    @Override
    public void removeEnrollment(Long userId) {
        withUserLock(userId, () -> {
            doRemoveEnrollment(userId);
            return null;
        });
    }

    @Override
    public FaceBindingEntity recognize(FaceImage image) {
        List<SearchMatch> matches;
        try {
            matches = insightFaceClient.search(image);
        } catch (InsightFaceApiException exception) {
            // 422：照片里没有人脸(face_not_found)或无法解码(invalid_image)，和「没认出是谁」分开提示
            if (exception.getStatus() == 422) {
                logger.info("[人脸登录] 照片不合格, code: {}", exception.getCode());
                throw new ServiceException(SystemErrorCodeConstants.FACE_IMAGE_REJECTED);
            }
            throw insightFaceClient.unexpected("搜索", exception);
        }

        List<SearchMatch> candidates = bestPerPerson(matches);
        if (candidates.isEmpty()) {
            throw reject("没有候选人", null, null);
        }
        SearchMatch first = candidates.getFirst();
        SearchMatch second = candidates.size() > 1 ? candidates.get(1) : null;
        if (first.similarity() < properties.getThreshold()) {
            throw reject("第一名低于判定阈值", first, second);
        }
        // 只有一个候选人员时不做分差校验
        if (second != null && first.similarity() - second.similarity() < properties.getMargin()) {
            throw reject("与第二名分差不足", first, second);
        }

        FaceBindingEntity binding = faceBindingRepository
                .findByCollectionIdAndPersonIdAndDeletedFalse(properties.getCollectionId(), first.personId())
                .orElseThrow(() -> reject("Person 没有有效绑定", first, second));
        if (!String.valueOf(binding.getUserId()).equals(first.externalId())) {
            throw reject("Person 的 external_id 与绑定用户不一致", first, second);
        }
        logger.info("[人脸登录] 识别通过, userId: {}, personId: {}, similarity: {}, second: {}",
                binding.getUserId(), first.personId(), format(first), format(second));
        return binding;
    }

    @Override
    public void recordLogin(FaceBindingEntity binding) {
        try {
            binding.setLastLoginTime(LocalDateTime.now());
            faceBindingRepository.save(binding);
        } catch (RuntimeException exception) {
            logger.warn("[人脸登录] 更新最近登录时间失败, bindingId: {}, message: {}",
                    binding.getId(), exception.getMessage());
        }
    }

    /** 不加锁的关闭流程，供已经持有单用户锁的方法调用。 */
    private void doRemoveEnrollment(Long userId) {
        String personId = personIdOf(userId);
        // 先删 Person：失败时抛 C0001 / C0200，绑定保持不变，可以重试
        insightFaceClient.deletePerson(personId);
        faceBindingRepository.findByUserIdAndDeletedFalse(userId).ifPresent(binding -> {
            binding.setDeleted(true);
            faceBindingRepository.saveAndFlush(binding);
            logger.info("[人脸登录] 已关闭, userId: {}, personId: {}", userId, binding.getPersonId());
        });
    }

    /**
     * 按 Person 分组，每人取最高相似度，再按相似度降序。官方结果已按 Person 汇总，
     * 这一步不改变结果，只是防止同一人的多个样本占据前两名、分差为 0 时把本人拒掉。
     */
    private static List<SearchMatch> bestPerPerson(List<SearchMatch> matches) {
        Map<String, SearchMatch> best = new LinkedHashMap<>();
        for (SearchMatch match : matches) {
            best.merge(match.personId(), match,
                    (current, candidate) -> candidate.similarity() > current.similarity() ? candidate : current);
        }
        return best.values().stream()
                .sorted(Comparator.comparingDouble(SearchMatch::similarity).reversed())
                .toList();
    }

    /** 统一的「人脸识别未通过」，具体原因和相似度只写日志，不告诉前端。 */
    private static ServiceException reject(String reason, SearchMatch first, SearchMatch second) {
        logger.info("[人脸登录] 未通过, 原因: {}, first: {} {}, second: {} {}", reason,
                first == null ? "-" : first.personId(), format(first),
                second == null ? "-" : second.personId(), format(second));
        return new ServiceException(SystemErrorCodeConstants.FACE_LOGIN_REJECTED);
    }

    private static String format(SearchMatch match) {
        return match == null ? "-" : String.format("%.4f", match.similarity());
    }

    private FaceEnrollRespVO doEnroll(LoginUser loginUser, List<FaceImage> images, LocalDateTime consentTime) {
        Long userId = loginUser.getUserId();
        if (faceBindingRepository.findByUserIdAndDeletedFalse(userId).isPresent()) {
            throw new ServiceException(SystemErrorCodeConstants.FACE_ALREADY_ENROLLED);
        }
        String personId = personIdOf(userId);
        EnrollResult result = createPersonReplacingLeftover(personId, loginUser, images);
        if (result.allRejected()) {
            // InsightFace 全部拒绝时不创建 Person，MES 也不写绑定
            logger.info("[人脸注册] 全部照片被拒, userId: {}, rejected: {}", userId, result.rejectedImages().size());
            return toEnrollResp(result, 0);
        }

        FaceBindingEntity binding = new FaceBindingEntity();
        binding.setUserId(userId);
        binding.setCollectionId(properties.getCollectionId());
        binding.setPersonId(personId);
        binding.setConsentTime(consentTime);
        binding.setDeleted(false);
        try {
            faceBindingRepository.saveAndFlush(binding);
        } catch (RuntimeException exception) {
            // 绑定写不进去时立刻删除 Person，避免出现 MES 没有绑定、人脸库里却能搜到的人
            rollbackPerson(personId);
            if (exception instanceof DataIntegrityViolationException) {
                throw new ServiceException(SystemErrorCodeConstants.FACE_ALREADY_ENROLLED);
            }
            throw exception;
        }
        logger.info("[人脸注册] 成功, userId: {}, personId: {}, accepted: {}, rejected: {}",
                userId, personId, result.faceIds().size(), result.rejectedImages().size());
        return toEnrollResp(result, result.faceIds().size());
    }

    /**
     * 创建 Person；遇到 409 person_exists 说明上次删除失败留下了残留，删除后重建一次。
     * 用户主动关闭过人脸登录，旧特征不应保留，因此不复用旧 Person。
     */
    private EnrollResult createPersonReplacingLeftover(String personId, LoginUser loginUser, List<FaceImage> images) {
        String externalId = String.valueOf(loginUser.getUserId());
        try {
            return insightFaceClient.createPerson(personId, externalId, loginUser.getUserName(), images);
        } catch (InsightFaceApiException exception) {
            if (!exception.is(409, "person_exists")) {
                throw insightFaceClient.unexpected("创建 Person", exception);
            }
        }
        logger.warn("[人脸注册] 发现残留 Person，删除后重建, personId: {}", personId);
        insightFaceClient.deletePerson(personId);
        try {
            return insightFaceClient.createPerson(personId, externalId, loginUser.getUserName(), images);
        } catch (InsightFaceApiException exception) {
            throw insightFaceClient.unexpected("重建 Person", exception);
        }
    }

    private void rollbackPerson(String personId) {
        try {
            insightFaceClient.deletePerson(personId);
        } catch (RuntimeException exception) {
            // 删除失败时留下残留 Person；下次注册遇到 409 会先删后建，不会影响正确性
            logger.error("[人脸注册] 写绑定失败后删除 Person 也失败, personId: {}", personId);
        }
    }

    /** 绑定存在但 Person 已不在人脸库：把绑定标为已删除，按未注册处理。 */
    private void healBinding(FaceBindingEntity binding) {
        logger.warn("[人脸登录] 绑定对应的 Person 不存在，绑定标为已删除, userId: {}, personId: {}",
                binding.getUserId(), binding.getPersonId());
        binding.setDeleted(true);
        faceBindingRepository.saveAndFlush(binding);
    }

    private <T> T withUserLock(Long userId, Supplier<T> action) {
        String token = faceUserLockRedisDAO.tryLock(userId)
                .orElseThrow(() -> new ServiceException(SystemErrorCodeConstants.FACE_OPERATION_IN_PROGRESS));
        try {
            return action.get();
        } finally {
            faceUserLockRedisDAO.unlock(userId, token);
        }
    }

    private static boolean isAdmin(LoginUser loginUser) {
        return loginUser.getRoleCodes() != null && loginUser.getRoleCodes().contains(RoleCodeConstants.ADMIN);
    }

    private static String personIdOf(Long userId) {
        return PERSON_ID_PREFIX + userId;
    }

    private static FaceEnrollRespVO toEnrollResp(EnrollResult result, int faceCount) {
        FaceEnrollRespVO response = new FaceEnrollRespVO();
        response.setAcceptedCount(result.faceIds().size());
        response.setFaceCount(faceCount);
        response.setRejectedImages(result.rejectedImages().stream().map(FaceServiceImpl::toRejectedVO).toList());
        return response;
    }

    private static FaceEnrollRespVO.RejectedImage toRejectedVO(RejectedImage rejected) {
        FaceEnrollRespVO.RejectedImage vo = new FaceEnrollRespVO.RejectedImage();
        vo.setIndex(rejected.index());
        vo.setReason(rejected.reason());
        return vo;
    }

    private static FaceProfileRespVO.Sample toSampleVO(FaceSample sample) {
        FaceProfileRespVO.Sample vo = new FaceProfileRespVO.Sample();
        vo.setFaceId(sample.faceId());
        vo.setCreatedAt(sample.createdAt());
        return vo;
    }
}
