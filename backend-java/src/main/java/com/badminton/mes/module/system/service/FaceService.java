package com.badminton.mes.module.system.service;

import java.util.List;

import com.badminton.mes.common.security.LoginUser;
import com.badminton.mes.module.system.controller.vo.FaceEnrollRespVO;
import com.badminton.mes.module.system.controller.vo.FaceProfileRespVO;

import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸登录注册与样本管理服务。
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
     * 查询本人人脸登录状态。
     *
     * @param loginUser 当前登录用户
     * @return 状态与样本列表
     */
    FaceProfileRespVO getProfile(LoginUser loginUser);

    /**
     * 关闭指定用户的人脸登录：删除 InsightFace Person(不存在视为成功)，再把有效绑定标为已删除。
     * 没有绑定时也会尝试删除 Person，顺带清理残留。
     *
     * @param userId 用户主键
     */
    void removeEnrollment(Long userId);
}
