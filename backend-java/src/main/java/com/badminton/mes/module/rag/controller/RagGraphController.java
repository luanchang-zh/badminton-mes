package com.badminton.mes.module.rag.controller;

import java.util.List;

import com.badminton.mes.common.core.CommonResult;
import com.badminton.mes.module.rag.controller.vo.RagGraphRespVO;
import com.badminton.mes.module.rag.service.RagQueryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 知识库图谱 Controller。
 *
 * <p>只读接口，登录即可访问：图谱可视化是知识库的核心体验之一，不设角色门槛。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
@RestController
@RequestMapping("/api/rag/graph")
public class RagGraphController {

    private final RagQueryService ragQueryService;

    /**
     * 构造器注入，依赖不可变。
     *
     * @param ragQueryService 问答与图谱 Service
     */
    public RagGraphController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    /**
     * 列出当前租户图谱中的全部标签。
     *
     * @return 标签列表
     */
    @GetMapping("/labels")
    public CommonResult<List<String>> listLabels() {
        return CommonResult.success(ragQueryService.listGraphLabels());
    }

    /**
     * 按标签查询当前租户的子图。
     *
     * <p>{@code maxDepth} 限制在 1~3：图谱展开深度每加一层，节点数量会快速增长，
     * 不设上限时一次请求可能返回上万节点，前端渲染会直接卡死。
     *
     * @param label    实体标签
     * @param maxDepth 最大展开深度，缺省 2
     * @return 节点与边
     */
    @GetMapping
    public CommonResult<RagGraphRespVO> getGraph(
            @RequestParam("label") @NotBlank(message = "标签不能为空") String label,
            @RequestParam(value = "maxDepth", defaultValue = "2")
            @Min(value = 1, message = "展开深度不能小于 1")
            @Max(value = 3, message = "展开深度不能大于 3") int maxDepth) {
        return CommonResult.success(
                RagGraphRespVO.from(ragQueryService.getGraph(label, maxDepth)));
    }
}
