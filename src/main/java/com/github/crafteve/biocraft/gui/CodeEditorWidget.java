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
    /** 未解锁字段行颜色（暗灰，kcat 等后续解锁字段） */
    private static final int LOCKED_LINE_COLOR = 0xFF6A6A6A;
    /** 酶设计单字段关键词（DSL：id/name/kcat/input/output，大小写不敏感） */
    private static final String[] FIELD_KEYWORDS = {"id", "name", "kcat", "input", "output"};

    // ---- 逐字符编码动画配色（编码波峰 → 渐变冷却） ----
    /** 波峰字符文字（金色，正在编码） */
    private static final int ENCODED_PEAK_TEXT = 0xFFFFE08A;
    /** 波峰字符发光晕（半透明金，外扩 1px） */
    private static final int ENCODED_PEAK_GLOW = 0x44FFE08A;
    /** 波峰背景脉动上限色（亮绿） */
    private static final int ENCODED_PEAK_BG = 0xFF3E9E52;
    /** 新鲜已编码文字（亮绿白） */
    private static final int ENCODED_FRESH_TEXT = 0xFFC9F5D2;
    /** 陈旧已编码文字（暗绿，冷却后） */
    private static final int ENCODED_OLD_TEXT = 0xFF5E9E6E;
    /** 新鲜已编码底色（亮绿） */
    private static final int ENCODED_FRESH_BG = 0xFF2E7A3E;
    /** 陈旧已编码底色（深绿，冷却后） */
    private static final int ENCODED_OLD_BG = 0xFF14291A;
    /** 渐变跨度（字符数，波峰向后多少个字符完成冷却） */
    private static final int FADE_CHARS = 15;

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
    private int flashTicks;        // 编码完成闪光倒计时（完成瞬间全文本闪白）
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

    /** 编码进度（0~1），驱动逐字符动画；从 <1 变为 1 时触发完成闪光 */
    public void setProgress(double progress) {
        if (this.progress < 1.0 && progress >= 1.0) {
            this.flashTicks = 25; // 编码完成：全文本白光闪烁
        }
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

        // 逐字符编码动画（动画 A）：全局字符配额（含换行符），进度推进时
        // 字符逐个进入编码态（变色 + 变底色）——取代旧"整行扫描线"动画
        int totalChars = text.length();
        int encodedChars = progress < 1.0 ? (int) Math.round(progress * totalChars) : 0;

        int textX = x + 3;
        int textY = y + 2;
        int globalChar = 0;
        for (int i = firstVisibleLine; i < Math.min(lines.length, firstVisibleLine + visibleLines); i++) {
            int lineY = textY + (i - firstVisibleLine) * lineHeight();
            String lineText = lines[i];
            // 本行已编码字符数（行首连续段，编码顺序逐字符；完成闪光期间按全长）
            int lineEncoded = (progress < 1.0 || flashTicks > 0)
                    ? Math.max(0, Math.min(lineText.length(), encodedChars - globalChar))
                    : 0;
            // 未解锁字段（kcat）整行灰显 + 行尾"（未解锁）"提示（教学引导，不阻止输入）
            if (isLockedFieldLine(lineText)) {
                graphics.drawString(font, lineText, textX, lineY, LOCKED_LINE_COLOR, false);
                int textW = font.width(lineText);
                graphics.drawString(font, "（未解锁）", textX + textW + 6, lineY, LOCKED_LINE_COLOR, false);
            } else {
                drawHighlightedLine(graphics, lineText, textX, lineY, lineEncoded);
            }
            // 编码完成闪光：已编码区域叠加白色脉动光
            if (flashTicks > 0 && lineEncoded > 0) {
                int encW = font.width(lineText.substring(0, Math.min(lineEncoded, lineText.length())));
                float f = flashTicks / 25.0f;
                int alpha = (int) (0x50 * f * (0.6 + 0.4 * Math.sin(tickCount * 0.6)));
                if (alpha > 0) {
                    graphics.fill(textX, lineY, textX + encW, lineY + lineHeight(), (alpha << 24) | 0xFFFFFF);
                }
            }
            globalChar += lineText.length() + 1; // +1 换行符
        }
        if (flashTicks > 0) {
            flashTicks--;
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

    /**
     * 逐行绘制：行首 encodedCount 个字符为"已编码"——逐字符渐变动画
     * （波峰金色发光 + 向后冷却成暗绿），其余字符正常语法高亮
     */
    private void drawHighlightedLine(GuiGraphics graphics, String line, int x, int y, int encodedCount) {
        // 已编码段：逐字符绘制（age = 该字符已编码多久，1 = 最新/波峰）
        int encoded = Math.min(encodedCount, line.length());
        for (int i = 0; i < encoded; i++) {
            int age = encoded - i;
            x = drawEncodedChar(graphics, line.charAt(i), x, y, age);
        }
        if (encoded >= line.length()) {
            return;
        }
        String rest = line.substring(encoded);
        int i = 0;
        int len = rest.length();
        while (i < len) {
            char c = rest.charAt(i);
            if (c == '#') {
                drawRun(graphics, rest.substring(i), x, y, COLOR_COMMENT);
                return;
            }
            if (c == '"') {
                int end = rest.indexOf('"', i + 1);
                if (end < 0) {
                    end = len - 1;
                }
                drawRun(graphics, rest.substring(i, end + 1), x, y, COLOR_STRING);
                x += font.width(rest.substring(i, end + 1));
                i = end + 1;
                continue;
            }
            if (Character.isDigit(c)) {
                int end = i;
                while (end < len && (Character.isDigit(rest.charAt(end)) || rest.charAt(end) == '.')) {
                    end++;
                }
                drawRun(graphics, rest.substring(i, end), x, y, COLOR_NUMBER);
                x += font.width(rest.substring(i, end));
                i = end;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int end = i;
                while (end < len && (Character.isLetterOrDigit(rest.charAt(end)) || rest.charAt(end) == '_')) {
                    end++;
                }
                String word = rest.substring(i, end);
                int color = COLOR_PLAIN;
                if (word.equals("import") || word.equals("as") || word.equals("修饰")
                        || isFieldKeyword(word)) {
                    color = COLOR_KEYWORD;
                } else if (end < len && rest.charAt(end) == '(') {
                    color = COLOR_FUNCTION;
                }
                drawRun(graphics, word, x, y, color);
                x += font.width(word);
                i = end;
                continue;
            }
            if (c == '=' || c == ',' || c == '(' || c == ')' || c == ';' || c == ':') {
                drawRun(graphics, String.valueOf(c), x, y, COLOR_SYMBOL);
                x += font.width(String.valueOf(c));
                i++;
                continue;
            }
            // 空格与其余字符
            String ch = String.valueOf(c);
            drawRun(graphics, ch, x, y, COLOR_PLAIN);
            x += font.width(ch);
            i++;
        }
    }

    /**
     * 单字符"已编码"绘制（华丽逐字符动画）：
     * <ul>
     *   <li>波峰（age=1，正在编码）：金色文字 + 外扩 1px 半透明金发光晕 +
     *       背景随 tick 脉动（呼吸灯）</li>
     *   <li>次新（age=2）：新鲜亮绿白 + 亮绿底</li>
     *   <li>更旧（age&gt;2）：按 FADE_CHARS 跨度从新鲜渐变冷却为暗绿</li>
     * </ul>
     *
     * @return 下一个字符的 x（本字符右缘）
     */
    private int drawEncodedChar(GuiGraphics graphics, char c, int x, int y, int age) {
        String s = String.valueOf(c);
        int w = font.width(s);
        int h = lineHeight();
        if (age == 1) {
            // 波峰：发光晕 + 脉动背景 + 金色文字
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, ENCODED_PEAK_GLOW);
            double pulse = 0.5 + 0.5 * Math.sin(tickCount * 0.3);
            int bg = lerpColor(ENCODED_FRESH_BG, ENCODED_PEAK_BG, (float) pulse);
            graphics.fill(x, y, x + w, y + h, bg);
            graphics.drawString(font, s, x, y, ENCODED_PEAK_TEXT, false);
            return x + w;
        }
        // 冷却渐变：新鲜 → 陈旧（文字与底色同步插值）
        float t = Math.min((float) (age - 1) / FADE_CHARS, 1.0f);
        int text = lerpColor(ENCODED_FRESH_TEXT, ENCODED_OLD_TEXT, t);
        int bg = lerpColor(ENCODED_FRESH_BG, ENCODED_OLD_BG, t);
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.drawString(font, s, x, y, text, false);
        return x + w;
    }

    /** 两色线性插值（alpha 恒 0xFF） */
    private static int lerpColor(int c1, int c2, float t) {
        int r = (c1 >> 16 & 0xFF) + (int) (((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * t);
        int g = (c1 >> 8 & 0xFF) + (int) (((c2 >> 8 & 0xFF) - (c1 >> 8 & 0xFF)) * t);
        int b = (c1 & 0xFF) + (int) (((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void drawRun(GuiGraphics graphics, String s, int x, int y, int color) {
        graphics.drawString(font, s, x, y, color, false);
    }

    /** 单词是否为酶设计单字段关键词（大小写不敏感） */
    private static boolean isFieldKeyword(String word) {
        for (String keyword : FIELD_KEYWORDS) {
            if (keyword.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    /** 行是否为未解锁字段行（当前仅 kcat；整行灰显 + 行尾提示） */
    private static boolean isLockedFieldLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        return "kcat".equalsIgnoreCase(trimmed.substring(0, colon).trim());
    }

    public static Component title() {
        return Component.literal("程序");
    }
}
