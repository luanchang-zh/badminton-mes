package com.badminton.mes.module.rag.sse;

/**
 * 流式问答的 SSE 事件名。
 *
 * <p>集中声明避免前端与后端各写一份字符串字面量：事件名一旦拼错，
 * 前端表现为「连接正常但没有任何内容」，排查成本很高。
 *
 * @author 张竹灏
 * @date 2026/11/08
 */
public final class RagSseEventNames {

    /** 参考片段，通常先于正文到达，data 为字符串数组 */
    public static final String REFERENCES = "references";

    public static final String REFERENCE_DETAILS = "reference_details";

    public static final String META = "meta";

    /** 正文增量，data 为文本片段 */
    public static final String DELTA = "delta";

    /** 上游处理失败，data 为错误结构，随后连接正常结束 */
    public static final String ERROR = "error";

    /** 正常结束，data 为完整答案与检索模式 */
    public static final String DONE = "done";

    private RagSseEventNames() {
    }
}
