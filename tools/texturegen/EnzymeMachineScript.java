// EnzymeMachineScript.java
// 酶工厂方块贴图生成脚本（试水版：仅正面 front，四类别主题色，4 帧动画）
// 设计依据：用户参考图（灰金属机身 + 生物反应腔 + 指示灯 + GPI 铭牌 + 通风口 + 铆钉），
// 紫色 → 类别主题色四档色阶（主题色来源与 MachineCategory.java 的 themeColor 同步）
// 编译：javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/*.java
// 运行：java -cp tools/texturegen/out EnzymeMachineScript
// 输出：tools/texturegen/output/enzyme/<ec>/ 下 4 帧原始图 + 8x 预览 + 堆叠动画图 + mcmeta

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EnzymeMachineScript {

    /** 类别主题色档案：ec 目录标签 + 主题色（0xRRGGBB，与 MachineCategory.java 同步） */
    record Theme(String ec, int color) {}

    /** 动画帧数（观察窗粒子/指示灯闪烁节奏） */
    private static final int FRAMES = 4;

    /** 在用类别主题色表（EC3/EC6 暂无酶，不生成） */
    private static List<Theme> themes() {
        return List.of(
                new Theme("ec1", 0x6FC3DF),   // EC1 氧化还原酶 蓝（GAPDH）
                new Theme("ec2", 0xFFA94D),   // EC2 转移酶 橙（HK）
                new Theme("ec4", 0xB57EDC),   // EC4 裂合酶 紫（参考图原色系）
                new Theme("ec5", 0xFFD966)    // EC5 异构酶 黄（PGI）
        );
    }

    /**
     * 颜色乘法压暗：rgb 各通道乘系数（保留不透明 alpha）
     *
     * @param rgb 0xRRGGBB 颜色
     * @param f   亮度系数 0~1
     * @return 压暗后的 0xRRGGBB
     */
    private static int shade(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 颜色压暗（暖色自动暖化修正）
     * <p>
     * 等比例乘法压暗会使橙/黄等暖色在低亮度下偏冷棕/橄榄绿（色相漂移），
     * 修正规则：暖色相（r≥g≥b）压暗结果红通道上浮 15%、蓝通道下压 15%，
     * 保持暖相（深橙红/金黄棕）；冷色相（蓝/紫）不受影响
     *
     * @param rgb 0xRRGGBB 颜色
     * @param f   亮度系数 0~1
     * @return 压暗后的 0xRRGGBB
     */
    private static int shadeWarm(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        int rs = (rgb >> 16) & 0xFF;
        int gs = (rgb >> 8) & 0xFF;
        int bs = rgb & 0xFF;
        if (rs >= gs && gs >= bs) {
            r = Math.min(255, (int) (r * 1.15f));
            b = (int) (b * 0.85f);
        }
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 颜色向白色混合：rgb 与白色按比例混合（提亮）
     *
     * @param rgb 0xRRGGBB 颜色
     * @param w   白色比例 0~1
     * @return 提亮后的 0xRRGGBB
     */
    private static int mixWhite(int rgb, float w) {
        int r = (int) (((rgb >> 16) & 0xFF) + (255 - ((rgb >> 16) & 0xFF)) * w);
        int g = (int) (((rgb >> 8) & 0xFF) + (255 - ((rgb >> 8) & 0xFF)) * w);
        int b = (int) ((rgb & 0xFF) + (255 - (rgb & 0xFF)) * w);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 绘制酶工厂正面贴图（32x32，frame 为动画帧号）
     * <p>
     * 构图（自参考图提炼，自上而下）：
     * 四角铆钉 → 顶部 GPI 铭牌 → 中央观察窗（类别色四档渐变 + 上浮粒子 +
     * 液面高光）→ 右侧指示灯（绿，随帧闪暗）→ 指示灯下方类别色按钮 →
     * 左下双插槽 → 底部横向通风口；机身金属灰四档渐变（左上亮右下暗），
     * 面板凹槽分界环绕元素
     *
     * @param t     类别主题色档案
     * @param frame 动画帧号 0~3
     * @return 32x32 画布
     */
    private static PixelCanvas front(Theme t, int frame) {
        PixelCanvas p = new PixelCanvas(32, 32);

        // 机身金属四档灰 + 轮廓 + 凹槽 + 部件色
        p.color("outline", "#17171D");
        p.color("bodyLightest", "#B9B9BE");
        p.color("bodyLight", "#A0A0A5");
        p.color("body", "#7A7A80");
        p.color("bodyDark", "#5A5A5F");
        p.color("bodyDarkest", "#45454B");
        p.color("groove", "#2E2E33");
        p.color("metal", "#B0B0B4");
        p.color("slotBlack", "#1E1E23");
        p.color("ledGreen", "#4ADE80");
        p.color("ledDim", "#2FA05A");
        p.color("ledHot", "#DFFFE8");
        // 类别色五档色阶（参考图紫色阶比例微调：暗 0.40x/中暗 0.62x/亮混白 0.28/高光 0.55/亮白 0.9）
        p.color("reacDark", 0xFF000000 | shadeWarm(t.color(), 0.40f));
        p.color("reacMidDark", 0xFF000000 | shadeWarm(t.color(), 0.62f));
        p.color("reac", 0xFF000000 | t.color());
        p.color("reacBright", 0xFF000000 | mixWhite(t.color(), 0.28f));
        p.color("reacGlow", 0xFF000000 | mixWhite(t.color(), 0.55f));
        p.color("reacHot", 0xFF000000 | mixWhite(t.color(), 0.9f));

        // 机身：中灰打底，上/左两行提亮、下/右两行压暗（左上光源惯例）
        p.fill("body");
        p.hline(1, 30, 1, "bodyLight");
        p.hline(1, 30, 2, "bodyLight");
        p.vline(1, 30, 1, "bodyLight");
        p.vline(1, 30, 2, "bodyLight");
        p.hline(1, 30, 29, "bodyDark");
        p.hline(1, 30, 30, "bodyDark");
        p.vline(1, 30, 29, "bodyDark");
        p.vline(1, 30, 30, "bodyDark");
        p.outline(0, 0, 31, 31, "outline");

        // 面板凹槽分界（3..28 矩形凹线）
        p.outline(3, 3, 28, 28, "groove");

        // 四角铆钉（2x2 对角：左上亮右下暗模拟球面）
        p.set(2, 2, "bodyLightest");
        p.set(3, 2, "bodyLight");
        p.set(2, 3, "bodyLight");
        p.set(3, 3, "bodyDarkest");
        p.set(29, 2, "bodyLightest");
        p.set(28, 2, "bodyLight");
        p.set(29, 3, "bodyLight");
        p.set(28, 3, "bodyDarkest");
        p.set(2, 29, "bodyLightest");
        p.set(3, 29, "bodyLight");
        p.set(2, 28, "bodyLight");
        p.set(3, 28, "bodyDarkest");
        p.set(29, 29, "bodyLightest");
        p.set(28, 29, "bodyLight");
        p.set(29, 28, "bodyLight");
        p.set(28, 28, "bodyDarkest");

        // 顶部 GPI 铭牌：金属框 + 深灰底 + 类别高光色 3x5 像素字（内容区 5 行高，文字不压框）
        p.rect(4, 4, 20, 10, "metal");
        p.rect(5, 5, 19, 9, "groove");
        drawLetter(p, 6, 5, "G", "reacHot");
        drawLetter(p, 10, 5, "P", "reacHot");
        drawLetter(p, 14, 5, "I", "reacHot");

        // 中央观察窗：双层框（凹槽外框 + 金属内框），液体区 x7..18 y13..22
        p.outline(5, 11, 20, 24, "groove");
        p.outline(6, 12, 19, 23, "metal");
        // 液体逐行线性渐变：高光带（y13 特亮）→ 底部深色（暖色经暖化修正），无分档硬切
        liquidGradient(p, 7, 18, 13, 22,
                p.c("reacGlow"), 0xFF000000 | shadeWarm(t.color(), 0.32f));
        // 粒子：三颗 2x2 亮点（高光底 + 亮白核），随帧上浮循环（周期 9 格）
        int[] px = {9, 12, 16};
        for (int i = 0; i < px.length; i++) {
            int py = 21 - ((frame * 3 + i * 3) % 9);
            p.set(px[i], py, "reacGlow");
            p.set(px[i] + 1, py, "reacGlow");
            p.set(px[i], py + 1, "reacGlow");
            p.set(px[i] + 1, py + 1, "reacHot");
        }

        // 右侧指示灯（绿 3x3，随帧明暗交替模拟运行闪烁）
        p.rect(24, 11, 27, 15, "groove");
        p.rect(25, 12, 26, 14, frame % 2 == 0 ? "ledGreen" : "ledDim");
        p.set(25, 12, "ledHot");
        p.set(26, 12, "ledHot");

        // 指示灯下方类别色按钮（顶部高光 + 底部双层阴影形成凸起立体感）
        p.rect(25, 19, 27, 21, "reac");
        p.set(25, 19, "reacBright");
        p.set(25, 20, "reacBright");
        p.hline(25, 27, 21, "reacDark");
        p.hline(25, 27, 20, "reacMidDark");

        // 左下双插槽（黑色内凹）
        p.rect(4, 26, 5, 27, "slotBlack");
        p.rect(7, 26, 8, 27, "slotBlack");

        // 底部横向通风口：凹底 + 三条金属格栅竖条
        p.rect(11, 26, 21, 29, "groove");
        p.rect(12, 26, 13, 29, "metal");
        p.rect(15, 26, 16, 29, "metal");
        p.rect(18, 26, 19, 29, "metal");

        return p;
    }

    /**
     * 垂直线性渐变填充（从 top 色到 bottom 色逐行插值）
     * <p>
     * 用于观察窗液体：分档色阶在窄区域会产生硬切感，
     * 逐行插值在 32x32 下最平滑；两色均须为不透明 0xRRGGBB
     *
     * @param p      目标画布
     * @param x0     矩形左边界
     * @param x1     矩形右边界
     * @param y0     渐变起始行（top 色）
     * @param y1     渐变结束行（bottom 色）
     * @param top    顶部颜色 0xRRGGBB
     * @param bottom 底部颜色 0xRRGGBB
     */
    private static void liquidGradient(PixelCanvas p, int x0, int x1, int y0, int y1, int top, int bottom) {
        int span = Math.max(1, y1 - y0);
        for (int y = y0; y <= y1; y++) {
            float t = (float) (y - y0) / span;
            int r = (int) (((top >> 16) & 0xFF) + ((((bottom >> 16) & 0xFF) - ((top >> 16) & 0xFF))) * t);
            int g = (int) (((top >> 8) & 0xFF) + ((((bottom >> 8) & 0xFF) - ((top >> 8) & 0xFF))) * t);
            int b = (int) ((top & 0xFF) + (((bottom & 0xFF) - (top & 0xFF))) * t);
            int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            for (int x = x0; x <= x1; x++) {
                p.set(x, y, argb);
            }
        }
    }

    /**
     * 3x5 像素字体绘制（描点法，色名取自画布调色板）
     * <p>
     * 点阵按位编码：每字符 3 位宽 x 5 位高，1=点亮 0=留空；
     * 仅支持 G/P/I 三字符（铭牌专用，扩字时在此补充）
     *
     * @param p    目标画布
     * @param x0   字符左上角横坐标
     * @param y0   字符左上角纵坐标
     * @param ch   字符（G/P/I）
     * @param name 调色板颜色名
     */
    private static void drawLetter(PixelCanvas p, int x0, int y0, String ch, String name) {
        int[][] glyph = switch (ch) {
            case "G" -> new int[][]{{1, 1, 1}, {1, 0, 0}, {1, 0, 1}, {1, 0, 1}, {0, 1, 1}};
            case "P" -> new int[][]{{1, 1, 0}, {1, 0, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 0}};
            case "I" -> new int[][]{{1, 1, 1}, {0, 1, 0}, {0, 1, 0}, {0, 1, 0}, {1, 1, 1}};
            default -> throw new IllegalArgumentException("未支持的铭牌字符: " + ch);
        };
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                if (glyph[row][col] == 1) {
                    p.set(x0 + col, y0 + row, name);
                }
            }
        }
    }

    /**
     * 程序入口：对四个在用类别各生成 4 帧正面贴图 + 8x 预览 + 堆叠动画图 + mcmeta
     *
     * @param args 未使用
     */
    public static void main(String[] args) throws IOException {
        String outRoot = "tools/texturegen/output/enzyme";
        for (Theme t : themes()) {
            String dir = outRoot + "/" + t.ec();
            List<PixelCanvas> frames = new ArrayList<>();
            for (int f = 0; f < FRAMES; f++) {
                PixelCanvas fc = front(t, f);
                frames.add(fc);
                fc.save(dir + "/front_" + f + ".png");
                fc.savePreview(dir + "/front_" + f + "_preview.png", 8);
            }
            // 堆叠成动画贴图（32x32 x 4 帧 = 32x128）+ 动画声明（每帧 4 tick，帧间插值）
            PixelCanvas.stackVertical(frames, dir + "/front.png");
            PixelCanvas.writeMcmeta(dir + "/front.png", 4, true);
            System.out.println("已生成 " + t.ec() + " 正面贴图 " + dir);
        }
        System.out.println("酶工厂正面贴图生成完成");
    }
}
