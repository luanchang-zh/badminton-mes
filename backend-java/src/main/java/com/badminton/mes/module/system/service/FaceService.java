package com.badminton.mes.module.system.service;

import java.util.List;

import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.module.system.client.FaceImage;
import com.badminton.mes.module.system.controller.vo.FaceEnrollRespVO;
import com.badminton.mes.module.system.controller.vo.FaceProfileRespVO;
import com.badminton.mes.module.system.dal.entity.FaceBindingEntity;

import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸登录注册、识别与样本管理服务。
 *
 * <p>特征和样本由 InsightFace 保存，MES 只维护 {@code sys_face_binding}。
 * 涉及 InsightFace 的写操作不放在数据库事务里，避免网络调用期间占用连接。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
public interface FaceService {

    /**
     * 首次注册本人人脸。
     *
     * @param loginUser 当前登录用户
     * @param images 2～3 张照片
     * @param consent 是否已勾选人脸信息处理同意
     * @return 注册结果；全部照片被拒时 acceptedCount 为 0
     */
    FaceEnrollRespVO enroll(LoginUser loginUser, List<MultipartFile> images, Boolean consent);

    /**
     * 重新采集本人全部样本。先校验上传，在同一用户锁内关闭旧注册并重新注册。
     *
     * <p>旧特征删除成功后，新照片全部拒收或注册失败时保持未注册状态，可重新提交。
     * 未注册用户也可调用。必须再次同意人脸信息处理说明。
     *
     * @param loginUser 当前登录用户
     * @param images 2～3 张新照片
     * @param consent 是否已勾选人脸信息处理同意
     * @return 本次采集结果，faceCount 仅计算新样本
     */
    FaceEnrollRespVO reenroll(LoginUser loginUser, List<MultipartFile> images, Boolean consent);

    /**
     * 查询本人人脸登录状态。
     *
     * @param loginUser 当前登录用户
     * @return 状态与样本列表
     */
    FaceProfileRespVO getProfile(LoginUser loginUser);

    /**
     * 为本人追加样本。
     *
     * @param loginUser 当前登录用户
     * @param images 至少 1 张照片
     * @return 追加结果，结构与注册相同
     */
    FaceEnrollRespVO addSamples(LoginUser loginUser, List<MultipartFile> images);

    /**
     * 删除本人的一张样本；删到 0 张时关闭人脸登录。
     *
     * @param loginUser 当前登录用户
     * @param faceId FaceSample id
     */
    void deleteSample(LoginUser loginUser, String faceId);

    /**
     * 关闭指定用户的人脸登录：删除 InsightFace Person(不存在视为成功)，再把有效绑定标为已删除。
     *
     * <p>没有有效绑定时也会删除 Person，顺带清理之前失败留下的残留。删除 Person 失败时抛 C0001 / C0200，
     * 绑定保持不变，调用方可以重试；删除用户依赖这一点保证「账号已删、特征仍在」不会发生。
     *
     * @param userId 用户主键
     */
    void removeEnrollment(Long userId);

    /**
     * 用一张照片识别出唯一的已注册用户：搜索 → 按人员去重 → 阈值与分差判定 → 核对绑定。
     *
     * @param image 已校验的照片
     * @return 命中的有效绑定
     * @throws com.badminton.mes.common.exception.ServiceException 未通过 A0200、照片不合格 A0700、
     *         人脸服务不可用 C0001 / 超时 C0200
     */
    FaceBindingEntity recognize(FaceImage image);

    /**
     * 记录人脸登录成功时间。写库失败只记日志，不影响已经发放的会话。
     *
     * @param binding 登录命中的绑定
     */
    void recordLogin(FaceBindingEntity binding);
}
