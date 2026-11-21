package com.badminton.mes.module.rag.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagAnswerTextTest {

    @Test
    void filtersReasoningAcrossEveryCharacterBoundary() {
        var filter = new RagAnswerText();
        var result = new StringBuilder();
        String upstream = "<think>内部规划</think>### 点检说明\n检查记录";
        for (char character : upstream.toCharArray()) {
            result.append(filter.accept(String.valueOf(character)));
        }
        assertThat(result.append(filter.finish()).toString()).isEqualTo("### 点检说明\n检查记录");
    }

    @Test
    void keepsCodeExamplesAndDropsInterruptedPrivateText() {
        String examples = "示例\n```xml\n<think>示例</think>\n```\n"
                + "~~~xml\n<think>示例</think>\n~~~~\n";
        assertThat(RagAnswerText.clean(examples + "<think>内部规划")).isEqualTo(examples);
        assertThat(RagAnswerText.clean("数量 < 5")).isEqualTo("数量 < 5");
        assertThat(RagAnswerText.clean("前缀 ` 未闭合 ~~~<think>内部</think>可见"))
                .isEqualTo("前缀 ` 未闭合 ~~~可见");
    }

    @Test
    void respectsListAndQuoteBoundariesForNestedFences() {
        String nested = "1. 检查示例\n   ```text\n   <think>字面示例</think>\n    ```\n";
        String quote = "> ```xml\n> <think>引用示例</think>\n> ```\n";
        String answer = "<think>不应出现</think>\n### 正文";
        assertThat(RagAnswerText.clean(nested + answer)).isEqualTo(nested + "\n### 正文");
        assertThat(RagAnswerText.clean(quote + answer)).isEqualTo(quote + "\n### 正文");
        assertThat(RagAnswerText.clean(nested.replace("    ```\n", "") + answer))
                .isEqualTo(nested.replace("    ```\n", "") + "\n### 正文");
    }

    @Test
    void blankLineInsideListFenceDoesNotLeakTheFollowingReasoning() {
        String code = "1. 示例\n   ```xml\n\n   <think>字面示例</think>\n   ```\n";
        String input = code + "<think>模型内部内容</think>### 公开正文";
        assertThat(RagAnswerText.clean(input)).isEqualTo(code + "### 公开正文");
    }
}
