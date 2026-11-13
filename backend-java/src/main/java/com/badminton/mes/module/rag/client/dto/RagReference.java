package com.badminton.mes.module.rag.client.dto;

import java.util.List;

/** 可展示的引用快照；文件名不含服务端目录。 */
public record RagReference(String referenceId, String sourceName, List<String> chunks) {
    public RagReference {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
