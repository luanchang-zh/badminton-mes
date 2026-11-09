package com.badminton.mes.module.system.controller;

import java.util.List;

import com.badminton.mes.common.core.CommonResult;
import com.badminton.mes.common.security.SecurityContextHolder;
import com.badminton.mes.module.system.controller.vo.FaceEnrollRespVO;
import com.badminton.mes.module.system.controller.vo.FaceProfileRespVO;
import com.badminton.mes.module.system.service.FaceService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本人人脸登录管理接口。
 *
 * <p>全部要求登录，只操作当前登录用户自己的人脸；接口不接收 personId，
 * Person 由当前用户的绑定决定。
 *
 * @author xxy-lh
 * @date 2026/11/09
 */
@RestController
@RequestMapping("/api/system/face")
public class FaceController {

    private final FaceService faceService;

    /**
     * 构造器注入。
     *
     * @param faceService 人脸服务
     */
    public FaceController(FaceService faceService) {
        this.faceService = faceService;
    }

    /**
     * 首次注册本人人脸。
     *
     * @param images 2～3 张照片
     * @param consent 是否同意人脸信息处理说明，必须为 true
     * @return 注册结果；全部照片被拒时 acceptedCount 为 0
     */
    @PostMapping(value = "/enroll", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<FaceEnrollRespVO> enroll(
            @RequestPart("images") List<MultipartFile> images,
            @RequestParam(value = "consent", required = false) Boolean consent) {
        return CommonResult.success(faceService.enroll(SecurityContextHolder.getRequiredLoginUser(), images, consent));
    }

    /**
     * 查询本人人脸登录状态。
     *
     * @return 是否已注册及样本列表
     */
    @GetMapping("/profile")
    public CommonResult<FaceProfileRespVO> getProfile() {
        return CommonResult.success(faceService.getProfile(SecurityContextHolder.getRequiredLoginUser()));
    }
}
