package com.badminton.mes.module.system.service;

import java.util.List;

import com.badminton.mes.module.system.controller.vo.AuthLoginReqVO;
import com.badminton.mes.module.system.controller.vo.AuthLoginRespVO;
import com.badminton.mes.module.system.controller.vo.AuthPasswordReqVO;
import com.badminton.mes.module.system.controller.vo.AuthProfileRespVO;
import com.badminton.mes.module.system.controller.vo.AuthRegisterReqVO;

import org.springframework.web.multipart.MultipartFile;

/**
 * 认证 Service 接口：登录、登出、当前用户、修改本人密码。
 *
 * <p>会话与防爆破策略见 wiki/15-认证与权限管理设计.md：
 * Redis 不透明 token、单设备登录、连续失败 5 次锁定 15 分钟。
 *
 * @author 张竹灏
 * @date 2026/07/09
 */
public interface AuthService {

    /**
     * 工号 + 密码登录。
     *
     * <p>账户不存在与密码错误统一返回"工号或密码错误"，防撞库探测账号；
     * 账户停用在密码校验通过后才提示。登录成功清空失败计数并写入会话，
     * 同一用户旧会话被互踢(单设备登录)。
     *
     * @param reqVO 登录请求
     * @return token 与用户信息
     * @throws com.badminton.mes.common.exception.ServiceException 凭据错误、
     *         锁定中或账户停用时抛出
     */
    AuthLoginRespVO login(AuthLoginReqVO reqVO);

    /**
     * 人脸登录：一张照片换取与工号密码登录相同的会话。
     *
     * <p>同一来源 IP 在固定窗口内失败达到上限后直接拒绝，不再调用人脸服务。人脸服务不可用或超时
     * 不计入失败；登录成功也不清零。
     *
     * @param images 上传的照片，必须恰好 1 张
     * @param clientIp 来源 IP，已经过 X-Real-IP 处理
     * @return token 与用户信息，字段与工号密码登录一致
     * @throws com.badminton.mes.common.exception.ServiceException 识别未通过 A0200、停用 A0202、
     *         管理员 A0220、限流 A0501、照片不合格 A0700/A0701、人脸服务异常 C0001/C0200
     */
    AuthLoginRespVO faceLogin(List<MultipartFile> images, String clientIp);

    /**
     * 注册小程序账号并分配一个允许自助注册的职位。
     *
     * @param reqVO 注册请求
     * @return 新用户主键
     */
    Long register(AuthRegisterReqVO reqVO);

    /**
     * 登出：删除当前请求 token 对应的会话，天然幂等。
     */
    void logout();

    /**
     * 查询当前登录用户信息，实时读库，手机号脱敏(SEC-002)。
     *
     * @return 当前用户信息
     * @throws com.badminton.mes.common.exception.ServiceException 用户已被删除时抛出
     */
    AuthProfileRespVO getProfile();

    /**
     * 修改本人密码：校验旧密码，成功后强制下线，客户端需重新登录。
     *
     * @param reqVO 修改密码请求
     * @throws com.badminton.mes.common.exception.ServiceException 旧密码错误时抛出
     */
    void changePassword(AuthPasswordReqVO reqVO);
}
