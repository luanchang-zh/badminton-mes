package com.badminton.mes.module.system.controller;

import java.util.List;

import com.badminton.mes.common.core.CommonResult;
import com.badminton.mes.module.system.client.FaceImage;
import com.badminton.mes.module.system.controller.vo.AuthLoginReqVO;
import com.badminton.mes.module.system.controller.vo.AuthLoginRespVO;
import com.badminton.mes.module.system.controller.vo.AuthPasswordReqVO;
import com.badminton.mes.module.system.controller.vo.AuthProfileRespVO;
import com.badminton.mes.module.system.controller.vo.AuthRegisterReqVO;
import com.badminton.mes.module.system.controller.vo.RoleRespVO;
import com.badminton.mes.module.system.service.AuthService;
import com.badminton.mes.module.system.service.RoleService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 认证 Controller。
 *
 * <p>login、face_login、register、registration_roles 免登录(白名单见 SecurityWebConfig)，
 * 其余接口经拦截器登录校验后进入。
 *
 * @author 张竹灏
 * @date 2026/07/09
 */
@RestController
@RequestMapping("/api/system/auth")
public class AuthController {

    private final AuthService authService;

    private final RoleService roleService;

    /**
     * 构造器注入，依赖不可变。
     *
     * @param authService 认证 Service
     */
    public AuthController(AuthService authService, RoleService roleService) {
        this.authService = authService;
        this.roleService = roleService;
    }

    /**
     * 工号 + 密码登录。
     *
     * @param reqVO 登录请求
     * @return token 与用户信息
     */
    @PostMapping("/login")
    public CommonResult<AuthLoginRespVO> login(@Valid @RequestBody AuthLoginReqVO reqVO) {
        return CommonResult.success(authService.login(reqVO));
    }

    /**
     * 人脸登录：一张照片换取与工号密码登录相同的会话。
     *
     * <p>来源 IP 取 {@code getRemoteAddr()}，Tomcat 已按 X-Real-IP 处理，不自行读取请求头。
     *
     * @param images multipart 字段 image，恰好 1 个文件
     * @param request 当前请求
     * @return token 与用户信息
     */
    @PostMapping(value = "/face_login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<AuthLoginRespVO> faceLogin(@RequestPart("image") List<MultipartFile> images,
                                                   HttpServletRequest request) {
        FaceImage.validateUploadSizes(images);
        return CommonResult.success(authService.faceLogin(images, request.getRemoteAddr()));
    }

    /**
     * 注册小程序账号。
     *
     * @param reqVO 注册请求
     * @return 新用户主键
     */
    @PostMapping("/register")
    public CommonResult<Long> register(@Valid @RequestBody AuthRegisterReqVO reqVO) {
        return CommonResult.success(authService.register(reqVO));
    }

    /**
     * 查询小程序注册页可选择的职位。
     *
     * @return 可注册职位列表
     */
    @GetMapping("/registration_roles")
    public CommonResult<List<RoleRespVO>> getRegistrationRoles() {
        return CommonResult.success(roleService.getRegistrationRoles());
    }

    /**
     * 登出，删除当前会话，幂等。
     *
     * @return 空数据成功响应
     */
    @PostMapping("/logout")
    public CommonResult<Void> logout() {
        authService.logout();
        return CommonResult.success(null);
    }

    /**
     * 查询当前登录用户信息。
     *
     * @return 当前用户信息，手机号脱敏
     */
    @GetMapping("/profile")
    public CommonResult<AuthProfileRespVO> getProfile() {
        return CommonResult.success(authService.getProfile());
    }

    /**
     * 修改本人密码，成功后当前会话失效，需重新登录。
     *
     * @param reqVO 修改密码请求
     * @return 空数据成功响应
     */
    @PutMapping("/password")
    public CommonResult<Void> changePassword(@Valid @RequestBody AuthPasswordReqVO reqVO) {
        authService.changePassword(reqVO);
        return CommonResult.success(null);
    }
}
