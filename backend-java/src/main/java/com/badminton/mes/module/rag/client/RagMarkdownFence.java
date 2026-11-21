package com.badminton.mes.module.rag.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别当前正文位置是否属于页面会渲染为代码的围栏。
 * 列表和引用先剥离自身前缀再判断围栏，语法与 RagAnswer 的块渲染一致。
 */
final class RagMarkdownFence {
    private static final Pattern OPENING = Pattern.compile("^ {0,3}(`{3,}|~{3,}).*$");
    private static final Pattern CLOSING = Pattern.compile("^[ \t]*(`+|~+)[ \t]*$");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}>[ \t]?");
    private static final Pattern LIST = Pattern.compile("^([ \t]*)([-+*]|\\d+[.)])[ \t]+(.*)$");
    private static final Pattern INDENT = Pattern.compile("^[ \t]+(?=\\S)");
    private static final Pattern BLANK = Pattern.compile("^[ \t]*$");
    private static final int MAX_DEPTH = 12;

    private RagMarkdownFence() {
    }

    static boolean containsNextText(CharSequence prefix) {
        // 占位字符使当前物理行成为非空行，避免列表的空行规则误判标签归属。
        String[] lines = (prefix.toString() + "<").split("\\r\\n?|\\n", -1);
        return containsLastLine(List.of(lines), 0);
    }

    private static boolean containsLastLine(List<String> lines, int depth) {
        if (depth >= MAX_DEPTH) {
            return false;
        }
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            Matcher opening = OPENING.matcher(line);
            if (opening.matches()) {
                int closing = findClosing(lines, index + 1, opening.group(1));
                if (closing == lines.size()) {
                    return true;
                }
                index = closing + 1;
                continue;
            }
            Block nested = containedBlock(lines, index);
            if (nested == null) {
                index++;
                continue;
            }
            if (nested.next() == lines.size()) {
                return containsLastLine(nested.body(), depth + 1);
            }
            index = nested.next();
        }
        return false;
    }

    private static int findClosing(List<String> lines, int index, String opening) {
        for (int next = index; next < lines.size(); next++) {
            Matcher closing = CLOSING.matcher(lines.get(next));
            if (closing.matches() && closing.group(1).startsWith(opening)) {
                return next;
            }
        }
        return lines.size();
    }

    private static Block containedBlock(List<String> lines, int index) {
        Matcher quote = QUOTE.matcher(lines.get(index));
        if (quote.lookingAt()) {
            List<String> body = new ArrayList<>();
            int next = index;
            while (next < lines.size() && quote.reset(lines.get(next)).lookingAt()) {
                body.add(lines.get(next++).substring(quote.end()));
            }
            return new Block(next, body);
        }
        Matcher item = LIST.matcher(lines.get(index));
        return item.matches() ? listItem(lines, index, item) : null;
    }

    private static Block listItem(List<String> lines, int index, Matcher item) {
        List<String> body = new ArrayList<>();
        body.add(item.group(3));
        int next = index + 1;
        while (next < lines.size()) {
            int continuation = next;
            while (continuation < lines.size() && BLANK.matcher(lines.get(continuation)).matches()) {
                continuation++;
            }
            if (continuation == lines.size()) {
                break;
            }
            String line = lines.get(continuation);
            Matcher indent = INDENT.matcher(line);
            if (!indent.lookingAt() || indent.end() <= item.group(1).length()) {
                break;
            }
            // 空行后有缩进续行时仍归当前列表项，例如围栏代码中的空白行。
            while (next < continuation) {
                body.add("");
                next++;
            }
            body.add(line.substring(Math.min(indent.end(), item.start(3))));
            next++;
        }
        return new Block(next, body);
    }

    private record Block(int next, List<String> body) {
    }
}
