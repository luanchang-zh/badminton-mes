package com.badminton.mes.module.rag.client.dto;

/** 上传原件的下载名和字节内容。 */
public record RagDocumentFile(String fileName, byte[] content) {
}
