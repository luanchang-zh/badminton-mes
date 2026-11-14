package com.badminton.mes.module.rag.client.dto;

/** Original UTF-8 text returned only from the resolved tenant's storage. */
public record RagDocumentContent(String docId, String fileName, String content) {
}
