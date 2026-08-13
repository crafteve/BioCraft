// GuiAssets.java
// 酶工厂 GUI 第一版贴图资产生成器
// 资产清单：浅色槽位、紫白渐变平衡条、平衡点指针（菱形）、当前浓度商点指针（圆形）
// 设计依据：248×340 卡片式单页仪表盘（布局草稿已确认），主题紫 #7050B0、纸白 #F7F5F0
// 编译：javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/*.java
// 运行：java -cp tools/texturegen/out GuiAssets

import java.io.IOException;

public class GuiAssets {

    /** 主题紫（写死常量，第一版；JSON 可调后置） */
    static final int PURPLE = 0xFF7050B0;
    static final int PURPLE_LIGHT = 0xFF9060D0;
    static final int PURPLE_DARK = 0xFF503080;
    static final int PAPER_WHITE = 0xFFF7F5F0;

    public static void main(String[] args) throws IOException {
        slotLight();
        balanceBar();
        keqPoint();
        qPoint();
        System.out.println("GUI 资产生成完成");
    }

    /**
     * 浅色槽位贴图 18×18
     * <p>
     * 结构（比 vanilla 槽位更浅但保留轮廓辨识度）：
     * 外框 1px 中灰、内底浅灰、顶部内侧 1px 白色高光、
     * 底部内侧 1px 深灰阴影、左右内侧各 1px 过渡灰（四边立体感）
     */
    private static void slotLight() throws IOException {
        PixelCanvas c = new PixelCanvas(18, 18);
        c.color("border", "#A8A8A4");
        c.color("base", "#DAD9D4");
        c.color("top", "#F2F1EC");
        c.color("bottom", "#8E8D88");
        c.color("side", "#C2C1BC");
        c.fill("base");
        c.outline(0, 0, 17, 17, "border");
        c.hline(1, 16, 1, "top");
        c.hline(1, 16, 16, "bottom");
        c.vline(1, 16, 1, "side");
        c.vline(1, 16, 16, "side");
        c.save("tools/texturegen/output/gui/slot_light.png");
        c.savePreview("tools/texturegen/output/gui/slot_light_preview.png", 8);
    }

    /**
     * 紫白渐变平衡条 78×10
     * <p>
     * 左端主题紫（底物侧）向右渐变至纸白（产物侧），
     * 外描边深紫；平衡点与当前点由独立指针贴图叠放
     */
    private static void balanceBar() throws IOException {
        PixelCanvas c = new PixelCanvas(78, 10);
        c.color("border", "#503080");
        c.fill("border");
        for (int x = 0; x < 76; x++) {
            float t = x / 75f;
            int r = (int) (0x70 + (0xF7 - 0x70) * t);
            int g = (int) (0x50 + (0xF5 - 0x50) * t);
            int b = (int) (0xB0 + (0xF0 - 0xB0) * t);
            int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            for (int y = 1; y <= 8; y++) {
                c.set(x + 1, y, argb);
            }
        }
        c.save("tools/texturegen/output/gui/balance_bar.png");
        c.savePreview("tools/texturegen/output/gui/balance_bar_preview.png", 8);
    }

    /**
     * 平衡点指针（菱形 9×9）：表示理论平衡位置（Keq 对应点）
     * <p>
     * 刻画：纸白菱形主体 + 深紫描边 + 内层深紫十字心（加深一档拉开层次）
     * + 顶部高光；与圆形当前点形成形状区分（一菱一圆）
     */
    private static void keqPoint() throws IOException {
        PixelCanvas c = new PixelCanvas(9, 9);
        int cx = 4;
        // 深紫描边菱形（半径 4）
        fillDiamond(c, cx, 4, 4, PURPLE_DARK);
        // 纸白主体（半径 3）
        fillDiamond(c, cx, 4, 3, PAPER_WHITE);
        // 深紫十字心（#6040A0 加深一档）
        c.set(cx, 4, 0xFF6040A0);
        c.set(cx, 3, 0xFF6040A0);
        c.set(cx, 5, 0xFF6040A0);
        c.set(cx - 1, 4, 0xFF6040A0);
        c.set(cx + 1, 4, 0xFF6040A0);
        // 顶部高光
        c.set(cx, 1, 0xFFFFFFFF);
        c.save("tools/texturegen/output/gui/keq_point.png");
        c.savePreview("tools/texturegen/output/gui/keq_point_preview.png", 8);
    }

    /**
     * 当前浓度商点指针（实心圆 9×9）：表示实时 Q 位置
     * <p>
     * 刻画：白色描边圆盘 + 亮紫实心主体（无十字心，与菱形 keq 点强烈区分）
     * + 左上高光点；白描边保证在紫色渐变条上显眼
     */
    private static void qPoint() throws IOException {
        PixelCanvas c = new PixelCanvas(9, 9);
        // 白色描边圆（半径 4）
        fillCircle(c, 4, 4, 4, 0xFFFFFFFF);
        // 亮紫实心主体（半径 3）
        fillCircle(c, 4, 4, 3, PURPLE_LIGHT);
        // 左上高光
        c.set(2, 2, 0xFFFFFFFF);
        c.set(3, 3, 0xFFB898F0);
        c.save("tools/texturegen/output/gui/q_point.png");
        c.savePreview("tools/texturegen/output/gui/q_point_preview.png", 8);
    }

    /** 以中心为基准填充菱形（曼哈顿距离判定） */
    private static void fillDiamond(PixelCanvas c, int cx, int cy, int r, int argb) {
        for (int y = cy - r; y <= cy + r; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                if (Math.abs(x - cx) + Math.abs(y - cy) <= r) {
                    c.set(x, y, argb);
                }
            }
        }
    }

    /** 以中心为基准填充圆形（欧氏距离判定） */
    private static void fillCircle(PixelCanvas c, int cx, int cy, int r, int argb) {
        for (int y = cy - r; y <= cy + r; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= r * r + r / 2) {
                    c.set(x, y, argb);
                }
            }
        }
    }
}
