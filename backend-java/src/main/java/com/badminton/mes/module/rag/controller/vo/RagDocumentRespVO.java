package com.badminton.mes.module.rag.controller.vo;

import java.util.List;

import com.badminton.mes.module.rag.client.dto.RagDocumentDTO;
import com.badminton.mes.module.rag.enums.RagDocumentStatus;

/**
 * 文档列表项。
 *
 * <p>同时返回归一化状态与中文描述：前端既可按状态值分支，也可直接展示描述。
 *
 * @param docId       文档标识
 * @param fileName    文件名
 * @param status      归一化状态
 * @param statusText  中文状态描述
 * @param rawStatus   上游原始状态，便于排查
 * @author 张竹灏
 * @date 2026/11/08
 */
public record RagDocumentRespVO(
        String docId,
        String fileName,
        String status,
        String statusText,
        String rawStatus) {

    /**
     * 从客户端 DTO 转换。
     *
     * @param dto 客户端返回的文档快照
     * @return 响应对象
     */
    public static RagDocumentRespVO from(RagDocumentDTO dto) {
        RagDocumentStatus status = dto.status();
        return new RagDocumentRespVO(
                dto.docId(),
                dto.fileName(),
                status.name(),
                status.getDescription(),
                dto.rawStatus());
    }

    /**
     * 批量转换。
     *
     * @param documents 客户端返回的文档快照
     * @return 响应列表，null 转为空列表
     */
    public static List<RagDocumentRespVO> fromAll(List<RagDocumentDTO> documents) {
        return documents == null ? List.of() : documents.stream().map(RagDocumentRespVO::from).toList();
    }
}
