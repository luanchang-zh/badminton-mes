package com.badminton.mes.module.rag.client;

import java.util.Locale;

/**
 * 按回答维护过滤状态。上游可以在任意字符间分片，因此标签仅在识别后才决定输出，
 * 思考内容不进入累计正文。Markdown 代码中的同名标签保留为示例。
 */
public final class RagAnswerText {
    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";

    private final StringBuilder visible = new StringBuilder();
    private final StringBuilder tag = new StringBuilder(CLOSE.length());
    private int reasoningDepth;

    /** 对同步回答或已保存的历史执行同一过滤规则。 */
    public static String clean(String content) {
        RagAnswerText filter = new RagAnswerText();
        filter.accept(content);
        filter.finish();
        return filter.answer();
    }

    /** 增量读取一段回答，只返回本段确认可见的内容。 */
    String accept(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        int start = visible.length();
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if (tag.length() > 0 || current == '<') {
                consumeTag(current);
            } else if (reasoningDepth == 0) {
                visible.append(current);
            }
        }
        return visible.substring(start);
    }

    String answer() {
        return visible.toString();
    }

    private void consumeTag(char current) {
        tag.append(current);
        String candidate = tag.toString().toLowerCase(Locale.ROOT);
        if (OPEN.equals(candidate) || CLOSE.equals(candidate)) {
            // 仅在完整标签处确认 Markdown 归属，普通分片无须重新解析已显示的正文。
            if (reasoningDepth == 0 && RagMarkdownFence.containsNextText(visible)) {
                visible.append(tag);
            } else {
                reasoningDepth = OPEN.equals(candidate) ? reasoningDepth + 1 : Math.max(0, reasoningDepth - 1);
            }
            tag.setLength(0);
            return;
        }
        if (OPEN.startsWith(candidate) || CLOSE.startsWith(candidate)) {
            return;
        }

        // 前缀失配时保留可能开始下一标签的左尖括号。
        boolean newTag = current == '<';
        if (reasoningDepth == 0) {
            visible.append(tag, 0, tag.length() - (newTag ? 1 : 0));
        }
        tag.setLength(0);
        if (newTag) {
            tag.append(current);
        }
    }

    /** 流结束时保留普通比较符，丢弃未闭合的思考内容和半截标签。 */
    String finish() {
        String tail = reasoningDepth == 0 && tag.length() < 3 ? tag.toString() : "";
        tag.setLength(0);
        visible.append(tail);
        return tail;
    }
}
