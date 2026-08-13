import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 酶工厂 GUI 布局草稿绘制器（一次性草稿工具，非正式资产）
 * <p>
 * 以 3 倍缩放绘制 248×340 的 GUI 布局（输出 744×1020 PNG），
 * 标注各卡片边界、槽位坐标与模块位置，供设计评审确认几何布局
 * <p>
 * 编译：javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/DraftLayout.java
 * 运行：java -cp tools/texturegen/out DraftLayout
 */
public final class DraftLayout {
    private static final int SCALE = 3;
    private static final int GUI_W = 248;
    private static final int GUI_H = 360;

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(GUI_W * SCALE, GUI_H * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        // 纸白背景 + GUI 外框
        g.setColor(new Color(0xF7F5F0));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(0x1A1A1A));
        g.setStroke(new BasicStroke(3));
        g.drawRect(4, 4, GUI_W * SCALE - 8, GUI_H * SCALE - 8);

        drawTitleCard(g);
        drawInputCard(g);
        drawDashboard(g);
        drawOutputCard(g);
        drawInventoryCard(g);

        g.dispose();
        File out = new File("tools/texturegen/output");
        out.mkdirs();
        ImageIO.write(image, "png", new File(out, "layout_draft.png"));
        System.out.println("已输出 tools/texturegen/output/layout_draft.png");
    }

    private static void s(int x, int y, int w, int h) {
    }

    private static void label(Graphics2D g, String text, int x, int y, Color color, int size, boolean bold) {
        g.setFont(new Font("Microsoft YaHei", bold ? Font.BOLD : Font.PLAIN, size));
        g.setColor(color);
        g.drawString(text, x * SCALE, y * SCALE);
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, Color fill, Color border) {
        g.setColor(fill);
        g.fillRect(x * SCALE, y * SCALE, w * SCALE, h * SCALE);
        g.setColor(border);
        g.setStroke(new BasicStroke(2));
        g.drawRect(x * SCALE, y * SCALE, w * SCALE, h * SCALE);
    }

    /** 标题卡：y 8~52（高 44） */
    private static void drawTitleCard(Graphics2D g) {
        rect(g, 8, 8, 232, 44, new Color(0xFFFFFF), new Color(0x888888));
        // 方块贴图 16×16
        rect(g, 12, 22, 16, 16, new Color(0x9A9A9A), new Color(0x555555));
        label(g, "方块贴图", 13, 18, new Color(0x777777), 11, false);
        // 大类名 中/英
        label(g, "裂解酶工厂", 33, 32, new Color(0x1A1A1A), 15, true);
        label(g, "LYASE FACTORY", 33, 45, new Color(0x888888), 11, false);
        // 紫框缩写
        rect(g, 96, 20, 30, 20, new Color(0xE8E0F0), new Color(0x7050B0));
        label(g, "PGI", 103, 34, new Color(0x7050B0), 14, true);
        // 全名
        label(g, "磷酸葡萄糖异构酶", 131, 32, new Color(0x1A1A1A), 13, false);
        label(g, "Phosphoglucose Isomerase", 131, 45, new Color(0x777777), 10, false);
        // T/P/pH 环境框
        rect(g, 186, 14, 50, 30, new Color(0xF0F0F0), new Color(0xAAAAAA));
        label(g, "T 298K", 190, 25, new Color(0x1A1A1A), 10, false);
        label(g, "P 1.00", 190, 35, new Color(0x1A1A1A), 10, false);
        label(g, "pH 7.00", 190, 45, new Color(0x1A1A1A), 10, false);
    }

    /** 输入卡：x 8~74, y 60~204（高 144），滚动视口 3 条目 */
    private static void drawInputCard(Graphics2D g) {
        rect(g, 8, 60, 66, 164, new Color(0xFFFFFF), new Color(0x888888));
        label(g, "输入卡 输入（底物）", 10, 70, new Color(0x1A1A1A), 12, true);
        drawSpeciesCard(g, 10, 78, 62, 38, "G6P", "葡萄糖-6-磷酸", "×32", 0.50, new Color(0xF0B040));
        drawSpeciesCard(g, 10, 120, 62, 38, "ATP", "三磷酸腺苷", "×16", 0.25, new Color(0xFFA94D));
        drawSpeciesCard(g, 10, 162, 62, 38, "Pi", "磷酸根离子", "×16", 0.25, new Color(0x6FC3DF));
        label(g, "可滚动：条目卡片 40px 高", 10, 213, new Color(0x777777), 10, false);
    }

    /** 单张物种子卡：槽位 + 缩写 + 数量 + 浓度条 */
    private static void drawSpeciesCard(Graphics2D g, int x, int y, int w, int h, String abbr, String name, String count, double progress, Color color) {
        rect(g, x, y, w, h, new Color(0xFAFAF8), new Color(0xBBBBBB));
        // 槽位 18×18（浅色版，与 slot_light 资产一致）
        rect(g, x + 3, y + 4, 18, 18, new Color(0xDAD9D4), new Color(0xB8B8B8));
        label(g, "槽18", x + 5, y + 16, new Color(0x555555), 9, false);
        label(g, abbr, x + 24, y + 10, new Color(0x1A1A1A), 12, true);
        label(g, count, x + 24, y + 21, new Color(0x666666), 10, false);
        // 浓度条
        g.setColor(new Color(0xE0E0E0));
        g.fillRect((x + 24) * SCALE, (y + 28) * SCALE, 32 * SCALE, 6 * SCALE);
        g.setColor(color);
        g.fillRect((x + 24) * SCALE, (y + 28) * SCALE, (int) (32 * progress) * SCALE, 6 * SCALE);
        label(g, "浓度", x + 24, y + 40, new Color(0x999999), 9, false);
    }

    /** 仪表盘：x 78~170, y 60~204 */
    private static void drawDashboard(Graphics2D g) {
        rect(g, 78, 60, 92, 164, new Color(0xFFFFFF), new Color(0x888888));
        // 方程式（中央上方）
        label(g, "G6P ⇌ F6P", 92, 74, new Color(0x7050B0), 17, true);
        label(g, "可逆反应", 108, 84, new Color(0x999999), 10, false);
        // 净速率条
        label(g, "净速率 v", 82, 98, new Color(0x1A1A1A), 11, true);
        g.setColor(new Color(0xE0E0E0));
        g.fillRect(82 * SCALE, 103 * SCALE, 78 * SCALE, 8 * SCALE);
        g.setColor(new Color(0x7050B0));
        g.fillRect(82 * SCALE, 103 * SCALE, 62 * SCALE, 8 * SCALE);
        label(g, "v=0.83", 112, 100, new Color(0x7050B0), 11, true);
        // 方向箭头
        label(g, ">>>", 108, 118, new Color(0x7050B0), 14, true);
        label(g, "正向反应占优", 88, 130, new Color(0x666666), 10, false);
        // 平衡条（紫白渐变 + 双指针）
        label(g, "平衡条", 82, 140, new Color(0x1A1A1A), 11, true);
        for (int i = 0; i < 78; i++) {
            float t = i / 77f;
            Color c = blend(new Color(0x7050B0), new Color(0xF7F5F0), t);
            g.setColor(c);
            g.fillRect((82 + i) * SCALE, 146 * SCALE, SCALE, 10 * SCALE);
        }
        // 双指针：Keq 平衡点（菱形，位置 x=108 示意）+ Q 当前点（圆形，位置 x=134 示意）
        g.setColor(new Color(0x503080));
        int[] dx = {0, 3, 0, -3};
        int[] dy = {-5, 0, 5, 0};
        int kx = 108, ky = 151;
        g.fillPolygon(new int[]{(kx + dx[0]) * SCALE, (kx + dx[1]) * SCALE, (kx + dx[2]) * SCALE, (kx + dx[3]) * SCALE},
                new int[]{(ky + dy[0]) * SCALE, (ky + dy[1]) * SCALE, (ky + dy[2]) * SCALE, (ky + dy[3]) * SCALE}, 4);
        g.setColor(new Color(0x9060D0));
        g.fillOval((134 - 4) * SCALE, (147) * SCALE, 8 * SCALE, 8 * SCALE);
        g.setColor(new Color(0xFFFFFF));
        g.drawOval((134 - 4) * SCALE, (147) * SCALE, 8 * SCALE, 8 * SCALE);
        label(g, "◆Keq 平衡点", 88, 160, new Color(0x503080), 10, true);
        label(g, "●Q 当前点", 128, 160, new Color(0x9060D0), 10, true);
        label(g, "Q/Keq=0.67", 108, 168, new Color(0x999999), 9, false);
        // v-t 图
        label(g, "v-t 图（5 秒窗口，粒度秒）", 82, 180, new Color(0x1A1A1A), 11, true);
        rect(g, 82, 186, 78, 24, new Color(0xFAFAF8), new Color(0xCCCCCC));
        g.setColor(new Color(0x7050B0));
        g.setStroke(new BasicStroke(2));
        for (int i = 0; i < 76; i++) {
            int py = 200 - (int) (6 * Math.sin(i / 7.0) + 3 * Math.sin(i / 2.3));
            g.drawLine((82 + i) * SCALE, py * SCALE, (83 + i) * SCALE, (py + 1) * SCALE);
        }
        // 秒刻度
        for (int sec = 0; sec <= 5; sec++) {
            int tx = 82 + sec * 15;
            label(g, sec + "s", tx, 216, new Color(0x999999), 9, false);
        }
        // 停摆红字位
        label(g, "停摆原因（红字位）", 88, 228, new Color(0xD7252F), 10, false);
    }

    private static Color blend(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** 输出卡：x 174~240, y 60~204 */
    private static void drawOutputCard(Graphics2D g) {
        rect(g, 174, 60, 66, 164, new Color(0xFFFFFF), new Color(0x888888));
        label(g, "输出卡 输出（产物）", 176, 70, new Color(0x1A1A1A), 12, true);
        drawSpeciesCard(g, 176, 78, 62, 38, "F6P", "果糖-6-磷酸", "×16", 0.25, new Color(0x60B060));
        drawSpeciesCard(g, 176, 120, 62, 38, "H+", "氢离子（H⁺）", "×3", 0.05, new Color(0x9BD1A8));
        drawSpeciesCard(g, 176, 162, 62, 38, "ADP", "二磷酸腺苷", "×12", 0.19, new Color(0xFFA94D));
        label(g, "可滚动：条目卡片 40px 高", 176, 213, new Color(0x777777), 10, false);
    }

    /** 背包卡：居中靠下，x 8~240, y 232~352（高 120）：标题行 + 3×9 主背包 + 1×9 快捷栏 */
    private static void drawInventoryCard(Graphics2D g) {
        rect(g, 8, 232, 232, 120, new Color(0xFFFFFF), new Color(0x888888));
        label(g, "背包卡 背包物品栏（必须有，居中靠下）", 10, 242, new Color(0x1A1A1A), 12, true);
        int startX = 12, startY = 248;
        for (int row = 0; row < 4; row++) {
            int rowY = startY + row * 20;
            // 白分割线 + 黑阴影（2px + 1px，槽位行间距）
            if (row > 0) {
                g.setColor(new Color(0xFFFFFF));
                g.fillRect(startX * SCALE, (rowY - 3) * SCALE, 162 * SCALE, 2 * SCALE);
                g.setColor(new Color(0x1A1A1A));
                g.fillRect(startX * SCALE, (rowY - 1) * SCALE, 162 * SCALE, 1 * SCALE);
            }
            for (int col = 0; col < 9; col++) {
                rect(g, startX + col * 18, rowY, 18, 18, new Color(0xDAD9D4), new Color(0xB8B8B8));
            }
        }
        label(g, "主背包 3×9（18px 统一槽位）", startX, startY + 3 * 20 + 12, new Color(0x777777), 10, false);
        label(g, "快捷栏 1×9", startX, startY + 4 * 20 + 12, new Color(0x777777), 10, false);
        label(g, "白分割线 2px + 黑阴影 1px", 88, 352, new Color(0x999999), 10, false);
    }
}
