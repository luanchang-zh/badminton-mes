package com.badminton.mes.module.rag.client;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import com.badminton.mes.common.exception.ServiceException;
import com.badminton.mes.module.rag.constants.RagErrorCodeConstants;
import org.springframework.web.multipart.MultipartFile;

/** 校验原件的格式并限定 Office 压缩包的解压开销。 */
final class RagFileSignature {
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final long MAX_EXPANDED_BYTES = 128L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 4096;
    private static final Map<String, String> OFFICE_PARTS = Map.of(
            ".docx", "word/document.xml", ".xlsx", "xl/workbook.xml", ".pptx", "ppt/presentation.xml");

    private RagFileSignature() {
    }

    static void validate(String fileName, MultipartFile file) {
        String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        // 文本文件在接收请求时校验 UTF-8，只有二进制格式需要再检验原件结构。
        if (!".pdf".equals(extension) && !OFFICE_PARTS.containsKey(extension)) {
            return;
        }
        try {
            if (".pdf".equals(extension)) {
                try (var input = file.getInputStream()) {
                    if (Arrays.equals(PDF_HEADER, input.readNBytes(PDF_HEADER.length))) {
                        return;
                    }
                }
            } else if (hasOfficePart(file, OFFICE_PARTS.get(extension))) {
                return;
            }
        } catch (EOFException | ZipException error) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_BINARY_INVALID);
        } catch (IOException error) {
            throw new ServiceException(RagErrorCodeConstants.UPLOAD_READ_FAILED);
        }
        throw new ServiceException(RagErrorCodeConstants.UPLOAD_BINARY_INVALID);
    }

    private static boolean hasOfficePart(MultipartFile file, String part) throws IOException {
        try (var archive = new ZipInputStream(file.getInputStream())) {
            long expanded = 0;
            int entries = 0;
            boolean hasPart = false;
            boolean hasContentTypes = false;
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                // 压缩后的上传大小无法约束解压开销，按实际解压字节统计。
                long budget = MAX_EXPANDED_BYTES - expanded;
                expanded += archive.skip(budget + 1);
                if (++entries > MAX_ARCHIVE_ENTRIES || expanded > MAX_EXPANDED_BYTES) {
                    throw new ServiceException(RagErrorCodeConstants.UPLOAD_TOO_LARGE);
                }
                hasPart |= part.equals(entry.getName()) && !entry.isDirectory();
                hasContentTypes |= "[Content_Types].xml".equals(entry.getName()) && !entry.isDirectory();
            }
            return hasPart && hasContentTypes;
        }
    }
}
