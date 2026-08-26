package com.github.crafteve.biocraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * 程序文本 tooltip 语法高亮（DNA 物品 Ctrl 查看用）
 * <p>
 * 纯 Component 构建（只依赖 MC 文本 API，不碰 Minecraft 客户端单例）——
 * 与 CodeEditorWidget 的 GUI 分词规则一致（同一配色、同一关键词表），
 * 服务端类加载安全（无客户端类引用）
 */
final class ProgramHighlight {

    /** 语法高亮配色（收口到 central/DslField.Highlight，改色只改 DslField） */
    private static final int COLOR_KEYWORD = com.github.crafteve.biocraft.central.DslField.Highlight.KEYWORD.color();
    private static final int COLOR_FUNCTION = com.github.crafteve.biocraft.central.DslField.Highlight.FUNCTION.color();
    private static final int COLOR_NUMBER = com.github.crafteve.biocraft.central.DslField.Highlight.NUMBER.color();
    private static final int COLOR_STRING = com.github.crafteve.biocraft.central.DslField.Highlight.STRING.color();
    private static final int COLOR_COMMENT = com.github.crafteve.biocraft.central.DslField.Highlight.COMMENT.color();
    private static final int COLOR_SYMBOL = com.github.crafteve.biocraft.central.DslField.Highlight.SYMBOL.color();
    private static final int COLOR_PLAIN = com.github.crafteve.biocraft.central.DslField.Highlight.PLAIN.color();

    private ProgramHighlight() {
    }

    /**
     * 程序全文 → 逐行高亮 Component（保留缩进格式；每行一个 Component，
     * tooltip 自动分行显示）
     *
     * @param program 解码出的程序文本（可含多行）
     * @return 高亮后的行列表
     */
    static List<Component> highlight(String program) {
        List<Component> lines = new ArrayList<>();
        String[] rows = program.split("\n", -1);
        for (String row : rows) {
            lines.add(highlightLine(row));
        }
        return lines;
    }

    /** 单行分词高亮（规则与 CodeEditorWidget.drawHighlightedLine 一致） */
    private static MutableComponent highlightLine(String line) {
        MutableComponent comp = Component.empty();
        int i = 0;
        int len = line.length();
        while (i < len) {
            char c = line.charAt(i);
            if (c == '#') {
                comp.append(run(line.substring(i), COLOR_COMMENT));
                return comp;
            }
            if (c == '"') {
                int end = line.indexOf('"', i + 1);
                if (end < 0) {
                    end = len - 1;
                }
                comp.append(run(line.substring(i, end + 1), COLOR_STRING));
                i = end + 1;
                continue;
            }
            if (Character.isDigit(c)) {
                int end = i;
                while (end < len && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '.')) {
                    end++;
                }
                comp.append(run(line.substring(i, end), COLOR_NUMBER));
                i = end;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int end = i;
                while (end < len && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_')) {
                    end++;
                }
                String word = line.substring(i, end);
                int color = COLOR_PLAIN;
                if (word.equals("import") || word.equals("as") || word.equals("修饰")
                        || isFieldKeyword(word)) {
                    color = COLOR_KEYWORD;
                } else if (end < len && line.charAt(end) == '(') {
                    color = COLOR_FUNCTION;
                }
                comp.append(run(word, color));
                i = end;
                continue;
            }
            if (c == '=' || c == ',' || c == '(' || c == ')' || c == ';' || c == ':') {
                comp.append(run(String.valueOf(c), COLOR_SYMBOL));
                i++;
                continue;
            }
            comp.append(run(String.valueOf(c), COLOR_PLAIN));
            i++;
        }
        return comp;
    }

    /** 单段文字着色 */
    private static MutableComponent run(String s, int color) {
        return Component.literal(s).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
    }

    /** 酶设计单字段关键词（收口 DslField，大小写不敏感） */
    private static boolean isFieldKeyword(String word) {
        return com.github.crafteve.biocraft.central.DslField.isFieldKeyword(word);
    }
}
