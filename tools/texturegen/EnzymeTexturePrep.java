// EnzymeTexturePrep.java
// 酶工厂贴图素材处理脚本：用户手绘正面分层素材 → 灰度化/拷贝 → 正式贴图
// 处理内容（坐标规格与用户确认）：
//   model (5).png 64x64 正面外壳 layer1 → 原样拷贝（不染色）
//   model (6).png 19x8  铭牌层         → 灰度化（只保留深浅信息，染色时乘主题色）
//   model (7).png 30x28 中间染色涂层   → 灰度化（同上）
//   占位贴图：侧/顶/底/背灰机身（后续用户素材替换）
// 灰度化算法：亮度 L = 0.299R + 0.587G + 0.114B，保留 alpha 通道
// 编译：javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/*.java
// 运行：java -cp tools/texturegen/out EnzymeTexturePrep
// 输出：tools/texturegen/output/enzyme/（原始图 + 8x 预览，预览供用户自查，本工具不读图审查）

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class EnzymeTexturePrep {

    /** 素材输入目录 */
    private static final String INPUT_DIR = "tools/texturegen/input";
    /** 输出根目录 */
    private static final String OUT_DIR = "tools/texturegen/output/enzyme";

    /**
     * 程序入口：处理三张素材 + 生成四张占位贴图
     *
     * @param args 未使用
     */
    public static void main(String[] args) throws IOException {
        File out = new File(OUT_DIR);
        out.mkdirs();

        // 正面外壳：原样拷贝（不染色），校验 64x64
        BufferedImage shell = readImage(INPUT_DIR + "/model (5).png");
        if (shell.getWidth() != 64 || shell.getHeight() != 64) {
            System.out.println("警告: model (5).png 尺寸 " + shell.getWidth() + "x" + shell.getHeight() + " 不是 64x64，仍按原样输出");
        }
        writeImage(shell, OUT_DIR + "/shell_front.png");
        writePreview(shell, OUT_DIR + "/shell_front_preview.png", 8);

        // 铭牌层：灰度化，尺寸 19x8（以文件实际尺寸为准）
        BufferedImage nameplate = toGrayscale(readImage(INPUT_DIR + "/model (6).png"));
        writeImage(nameplate, OUT_DIR + "/layer_nameplate.png");
        writePreview(nameplate, OUT_DIR + "/layer_nameplate_preview.png", 8);

        // 中间染色涂层：灰度化，尺寸 30x28
        BufferedImage content = toGrayscale(readImage(INPUT_DIR + "/model (7).png"));
        writeImage(content, OUT_DIR + "/layer_content.png");
        writePreview(content, OUT_DIR + "/layer_content_preview.png", 8);

        // 四张占位贴图（侧/背/顶/底，后续用户素材替换）
        placeholder("placeholder_side", 0, 0);
        placeholder("placeholder_back", 1, 0);
        placeholder("placeholder_top", 0, 1);
        placeholder("placeholder_bottom", 0, 0);

        System.out.println("酶工厂贴图素材处理完成 -> " + OUT_DIR);
    }

    /**
     * 生成一张占位灰机身贴图（32x32）
     * <p>
     * 构图与正面外壳风格统一：金属灰四档渐变（左上亮右下暗）+ 四角铆钉 +
     * 面板凹槽；side 追加横散热条、back 追加竖格栅条、top 追加中心铆点
     *
     * @param name  输出文件名（不带扩展名）
     * @param ribs  0=无散热条 1=横条 2=竖条
     * @param centerTop 顶面中心铆点 1=画 0=不画
     */
    private static void placeholder(String name, int ribs, int centerTop) throws IOException {
        PixelCanvas p = new PixelCanvas(32, 32);
        p.color("outline", "#17171D");
        p.color("bodyLight", "#A0A0A5");
        p.color("body", "#7A7A80");
        p.color("bodyDark", "#5A5A5F");
        p.color("bodyDarkest", "#45454B");
        p.color("bodyLightest", "#B9B9BE");
        p.color("groove", "#2E2E33");

        p.fill("body");
        p.hline(1, 30, 1, "bodyLight");
        p.vline(1, 30, 1, "bodyLight");
        p.hline(1, 30, 30, "bodyDark");
        p.vline(1, 30, 30, "bodyDark");
        p.outline(0, 0, 31, 31, "outline");
        p.outline(3, 3, 28, 28, "groove");
        // 四角铆钉（2x2 对角渐变）
        rivet(p, 2, 2);
        rivet(p, 27, 2);
        rivet(p, 2, 27);
        rivet(p, 27, 27);

        if (ribs == 1) {
            // 横向散热条：中间三组
            p.rect(5, 13, 26, 14, "groove");
            p.rect(5, 17, 26, 18, "groove");
            p.rect(5, 21, 26, 22, "groove");
        } else if (ribs == 2) {
            // 竖向格栅条
            p.rect(10, 6, 11, 25, "groove");
            p.rect(15, 6, 16, 25, "groove");
            p.rect(20, 6, 21, 25, "groove");
        }
        if (centerTop == 1) {
            rivet(p, 14, 14);
        }

        p.save(OUT_DIR + "/" + name + ".png");
        p.savePreview(OUT_DIR + "/" + name + "_preview.png", 8);
    }

    /**
     * 画单个铆钉（2x2：左上高光右下暗）
     *
     * @param p 目标画布
     * @param x 铆钉左上角横坐标
     * @param y 铆钉左上角纵坐标
     */
    private static void rivet(PixelCanvas p, int x, int y) {
        p.set(x, y, "bodyLightest");
        p.set(x + 1, y, "bodyLight");
        p.set(x, y + 1, "bodyLight");
        p.set(x + 1, y + 1, "bodyDarkest");
    }

    /**
     * 读取 PNG 图像
     *
     * @param path 文件路径
     * @return ARGB 图像
     */
    private static BufferedImage readImage(String path) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) {
            throw new IOException("无法读取图片: " + path);
        }
        return img;
    }

    /**
     * 灰度化：亮度 L = 0.299R + 0.587G + 0.114B，alpha 保留
     * <p>
     * 染色层贴图必须为灰阶：运行时 tint 是乘法（灰阶 x 主题色），
     * 灰阶保持原图明暗层次，且不产生跨色相漂移（暖色压暗偏棕问题天然消失）
     *
     * @param src 源图像（ARGB）
     * @return 灰度 ARGB 图像（RGB 三通道相等）
     */
    private static BufferedImage toGrayscale(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int l = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                dst.setRGB(x, y, (a << 24) | (l << 16) | (l << 8) | l);
            }
        }
        return dst;
    }

    /**
     * 写出 PNG（自动创建父目录）
     *
     * @param img  图像
     * @param path 输出路径
     */
    private static void writeImage(BufferedImage img, String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
    }

    /**
     * 写出最近邻放大预览（供用户自查）
     *
     * @param img   源图像
     * @param path  输出路径
     * @param scale 放大倍数
     */
    private static void writePreview(BufferedImage img, String path, int scale) throws IOException {
        int w = img.getWidth() * scale;
        int h = img.getHeight() * scale;
        BufferedImage prev = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        prev.setRGB(x * scale + dx, y * scale + dy, argb);
                    }
                }
            }
        }
        writeImage(prev, path);
    }
}
