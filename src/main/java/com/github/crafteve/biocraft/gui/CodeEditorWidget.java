package com.github.crafteve.biocraft.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 自绘代码编辑器控件（DNA 编码器 GUI 的灵魂，IDE 风格）
 * <p>
 * 功能：多行编辑、缩进（Tab 插入 4 空格、回车继承上一行缩进）、
 * 简单语法高亮（关键字/函数/数字/字符串/注释/符号分色）、闪烁光标
 * （方向键/Home/End/Backspace/回车/鼠标点击定位）、超行数纵向滚动。
 * 附带"编码扫描线"动画（动画 A）：progress 0→1 时一条亮线从顶扫到底，
 * 已扫过的行渐变为暗绿色（表示"这段程序已被编码成 DNA"）。
 * <p>
 * 完全自绘（非 vanilla 控件），输入事件由宿主 Screen 路由到本控件
 */
public class CodeEditorWidget {

    /** 语法高亮配色 */
    private static final int COLOR_KEYWORD = 0xFF569CD6;   // 关键字蓝
    private static final int COLOR_FUNCTION = 0xFFC586C0;  // 函数紫
    private static final int COLOR_NUMBER = 0xFFB5CEA8;    // 数字绿
    private static final int COLOR_STRING = 0xFFCE9178;    // 字符串橙
    private static final int COLOR_COMMENT = 0xFF6A9955;   // 注释灰绿
    private static final int COLOR_SYMBOL = 0xFFD7BA7D;    // 符号金
    private static final int COLOR_PLAIN = 0xFFD4D4D4;     // 普通文字白
    /** 已完成（已编码）行的淡化色：与暗绿混合 */
    private static final int SCANNED_MIX = 0xFF2E4A2E;
    /** 扫描线颜色 */
    private static final int COLOR_SCANNER = 0xFF00E5FF;
    /** 当前行高亮底色 */
    private static final int COLOR_CURRENT_LINE = 0x22FFFFFF;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Font font;

    private final StringBuilder text = new StringBuilder();
    private int cursor;            // 光标（文本索引）
    private int firstVisibleLine;  // 纵向滚动
    private boolean active = true; // 输入焦点
    private double progress = 1.0; // 编码进度 0..1（1 = 未编码/已完成）
    private long tickCount;

    public CodeEditorWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.font = Minecraft.getInstance().font;
    }

    public void setText(String value) {
        text.setLength(0);
        text.append(value);
        cursor = text.length();
        firstVisibleLine = 0;
        ensureCursorVisible();
    }

    public String getText() {
        return text.toString();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** 编码进度（0~1），驱动扫描线动画 */
    public void setProgress(double progress) {
        this.progress = progress;
    }

    public void tick() {
        tickCount++;
    }

    // ------------------------------------------------------------------
    // 输入
    // ------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        active = true;
        int line = (int) ((mouseY - y) / lineHeight()) + firstVisibleLine;
        String[] lines = text.toString().split("\n", -1);
        line = Math.max(0, Math.min(line, lines.length - 1));
        int charX = Math.max(0, (int) (mouseX - x));
        // 按字符宽度定位（简单近似：二分找最接近的字符列）
        int col = 0;
        int best = 0;
        for (int i = 0; i < lines[line].length(); i++) {
            int w = font.width(lines[line].charAt(i) == ' ' ? " " : String.valueOf(lines[line].charAt(i)));
            if (col + w / 2 >= charX) {
                best = i;
                break;
            }
            col += w;
            best = i + 1;
        }
        cursor = indexOfLine(line) + best;
        ensureCursorVisible();
        return true;
    }

    /**
     * 滚轮纵向滚动（悬停在编辑区内时由宿主 Screen 转交）：
     * 按行翻页（向上 = 看更上方行），钳制 [0, 总行数 - 可见行数]；
     * 无滚动余量时返回 false（不消费事件）。
     * 注意：滚动只改 firstVisibleLine，不移动光标——光标跟随
     * （ensureCursorVisible）只在光标操作时触发，互不干扰
     */
    public boolean mouseScrolled(double verticalAmount) {
        String[] lines = text.toString().split("\n", -1);
        int visible = Math.max(1, height / lineHeight());
        int maxFirst = Math.max(0, lines.length - visible);
        if (maxFirst == 0) {
            return false;
        }
        int delta = verticalAmount > 0 ? -1 : 1;
        int target = Math.max(0, Math.min(firstVisibleLine + delta, maxFirst));
        if (target == firstVisibleLine) {
            return false;
        }
        firstVisibleLine = target;
        return true;
    }

    public boolean charTyped(char codePoint) {
        if (!active) {
            return false;
        }
        text.insert(cursor, codePoint);
        cursor++;
        ensureCursorVisible();
        return true;
    }

    /**
     * 按键处理：各分支只改光标/文本，末尾统一 ensureCursorVisible
     * （光标移动时跟随滚动，滚轮浏览的 firstVisibleLine 不被覆盖）
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    text.deleteCharAt(cursor - 1);
                    cursor--;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < text.length()) {
                    text.deleteCharAt(cursor);
                }
            }
            case GLFW.GLFW_KEY_ENTER -> {
                String indent = currentIndent();
                text.insert(cursor, "\n" + indent);
                cursor += 1 + indent.length();
            }
            case GLFW.GLFW_KEY_TAB -> {
                text.insert(cursor, "    ");
                cursor += 4;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(text.length(), cursor + 1);
            }
            case GLFW.GLFW_KEY_UP -> {
                moveLine(-1);
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveLine(1);
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = indexOfLine(currentLine());
            }
            case GLFW.GLFW_KEY_END -> {
                String[] lines = text.toString().split("\n", -1);
                int line = currentLine();
                cursor = indexOfLine(line) + lines[line].length();
            }
            default -> {
                return false;
            }
        }
        ensureCursorVisible();
        return true;
    }

    private void moveLine(int delta) {
        String[] lines = text.toString().split("\n", -1);
        int line = currentLine();
        int col = cursor - indexOfLine(line);
        int target = Math.max(0, Math.min(line + delta, lines.length - 1));
        cursor = indexOfLine(target) + Math.min(col, lines[target].length());
    }

    private int currentLine() {
        String before = text.substring(0, cursor);
        int line = 0;
        for (int i = 0; i < before.length(); i++) {
            if (before.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int indexOfLine(int line) {
        int idx = 0;
        int current = 0;
        while (current < line) {
            int nl = text.indexOf("\n", idx);
            if (nl < 0) {
                return text.length();
            }
            idx = nl + 1;
            current++;
        }
        return idx;
    }

    /** 当前行的前导空白（回车继承缩进） */
    private String currentIndent() {
        int lineStart = indexOfLine(currentLine());
        StringBuilder indent = new StringBuilder();
        for (int i = lineStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }
        return indent.toString();
    }

    private int lineHeight() {
        return font.lineHeight + 1;
    }

    /**
     * 光标跟随滚动：光标移动（点击/方向键/输入/回车等）后调用，
     * 保证光标所在行可见——只在此处调整 firstVisibleLine，
     * 与滚轮浏览（mouseScrolled 直接改 firstVisibleLine）互不干扰
     */
    private void ensureCursorVisible() {
        String[] lines = text.toString().split("\n", -1);
        int visible = Math.max(1, height / lineHeight());
        int maxFirst = Math.max(0, lines.length - visible);
        int curLine = currentLine();
        if (curLine < firstVisibleLine) {
            firstVisibleLine = Math.max(0, Math.min(curLine, maxFirst));
        } else if (curLine >= firstVisibleLine + visible) {
            firstVisibleLine = Math.max(0, Math.min(curLine - visible + 1, maxFirst));
        }
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    public void render(GuiGraphics graphics) {
        int visibleLines = Math.max(1, height / lineHeight());
        String[] lines = text.toString().split("\n", -1);

        // 光标所在行（仅绘制光标用；滚动跟随由光标操作时 ensureCursorVisible
        // 主动触发，render 不再自动拉回——滚轮浏览的 firstVisibleLine 不被覆盖）
        int curLine = currentLine();

        // 扫描线所在行（编码中）
        int scanLine = (int) (progress * lines.length);

        int textX = x + 3;
        int textY = y + 2;
        for (int i = firstVisibleLine; i < Math.min(lines.length, firstVisibleLine + visibleLines); i++) {
            int lineY = textY + (i - firstVisibleLine) * lineHeight();
            boolean scanned = progress < 1.0 && i < scanLine;
            boolean isScanLine = progress < 1.0 && i == scanLine;
            if (isScanLine) {
                graphics.fill(x, lineY - 1, x + width, lineY + lineHeight(), COLOR_CURRENT_LINE);
            }
            drawHighlightedLine(graphics, lines[i], textX, lineY, scanned);
        }

        // 扫描线（编码中，画在当前扫描行下缘）
        if (progress < 1.0) {
            int scanY = textY + Math.min(scanLine, lines.length - 1) * lineHeight() + lineHeight() - 1;
            scanY = Math.min(scanY, y + height - 2);
            graphics.fill(x + 1, scanY, x + width - 1, scanY + 2, COLOR_SCANNER);
        }

        // 光标（闪烁方块）
        if (active && (tickCount / 12) % 2 == 0) {
            int curLineVis = curLine - firstVisibleLine;
            if (curLineVis >= 0 && curLineVis < visibleLines) {
                String prefix = lines.length > 0 && curLine < lines.length
                        ? lines[curLine].substring(0, Math.min(cursor - indexOfLine(curLine), lines[curLine].length()))
                        : "";
                int cx = textX + font.width(prefix);
                int cy = textY + curLineVis * lineHeight();
                graphics.fill(cx, cy, cx + 1, cy + lineHeight() - 2, 0xFFE8E8E8);
            }
        }
    }

    /** 逐词语法高亮绘制（简单分词器） */
    private void drawHighlightedLine(GuiGraphics graphics, String line, int x, int y, boolean scanned) {
        int i = 0;
        int len = line.length();
        while (i < len) {
            char c = line.charAt(i);
            if (c == '#') {
                drawRun(graphics, line.substring(i), x, y, COLOR_COMMENT, scanned);
                return;
            }
            if (c == '"') {
                int end = line.indexOf('"', i + 1);
                if (end < 0) {
                    end = len - 1;
                }
                drawRun(graphics, line.substring(i, end + 1), x, y, COLOR_STRING, scanned);
                x += font.width(line.substring(i, end + 1));
                i = end + 1;
                continue;
            }
            if (Character.isDigit(c)) {
                int end = i;
                while (end < len && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '.')) {
                    end++;
                }
                drawRun(graphics, line.substring(i, end), x, y, COLOR_NUMBER, scanned);
                x += font.width(line.substring(i, end));
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
                if (word.equals("import") || word.equals("as") || word.equals("修饰")) {
                    color = COLOR_KEYWORD;
                } else if (end < len && line.charAt(end) == '(') {
                    color = COLOR_FUNCTION;
                }
                drawRun(graphics, word, x, y, color, scanned);
                x += font.width(word);
                i = end;
                continue;
            }
            if (c == '=' || c == ',' || c == '(' || c == ')' || c == ';') {
                drawRun(graphics, String.valueOf(c), x, y, COLOR_SYMBOL, scanned);
                x += font.width(String.valueOf(c));
                i++;
                continue;
            }
            // 空格与其余字符
            String ch = String.valueOf(c);
            drawRun(graphics, ch, x, y, COLOR_PLAIN, scanned);
            x += font.width(ch);
            i++;
        }
    }

    private void drawRun(GuiGraphics graphics, String s, int x, int y, int color, boolean scanned) {
        if (scanned) {
            color = mix(color, SCANNED_MIX);
        }
        graphics.drawString(font, s, x, y, color, false);
    }

    /** 与已完成色混合（已编码行的淡化） */
    private static int mix(int base, int target) {
        int r = (((base >> 16) & 0xFF) + ((target >> 16) & 0xFF)) / 2;
        int g = (((base >> 8) & 0xFF) + ((target >> 8) & 0xFF)) / 2;
        int b = ((base & 0xFF) + (target & 0xFF)) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static Component title() {
        return Component.literal("程序");
    }
}
