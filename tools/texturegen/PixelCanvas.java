// PixelCanvas.java
// 像素画绘图画布：提供低分辨率像素绘制原语与 PNG 导出
// 用途：用代码确定性生成 Minecraft 贴图，产物可版本管理、可参数化调色板、可反复迭代
// 为什么用 Java AWT 而非 Python PIL：本机 Python 环境损坏，而项目锁定的 JDK 21 自带 ImageIO，零外部依赖

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PixelCanvas {

    /** 画布宽度（像素） */
    private final int width;
    /** 画布高度（像素） */
    private final int height;
    /** 像素存储，ARGB 格式，索引为 y * width + x */
    private final int[] pixels;
    /** 命名调色板：颜色名 -> ARGB 值 */
    private final Map<String, Integer> palette = new HashMap<>();

    /**
     * 创建指定尺寸的画布，初始全部透明（0x00000000）
     *
     * @param width  画布宽度（像素）
     * @param height 画布高度（像素）
     */
    public PixelCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    /**
     * 注册一个命名颜色到调色板，支持 #RGB / #RRGGBB / #AARRGGBB 十六进制写法
     *
     * @param name 颜色名，后续绘图原语用此名引用
     * @param hex  十六进制颜色字符串
     * @return 当前画布，便于链式调用
     */
    public PixelCanvas color(String name, String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        int argb;
        switch (h.length()) {
            case 3 -> {
                int r = Integer.parseInt(h.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(h.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(h.substring(2, 3), 16) * 17;
                argb = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            case 6 -> argb = 0xFF000000 | (int) Long.parseLong(h, 16);
            case 8 -> argb = (int) Long.parseLong(h, 16);
            default -> throw new IllegalArgumentException("无法解析颜色 " + hex + "，仅支持 #RGB/#RRGGBB/#AARRGGBB");
        }
        palette.put(name, argb);
        return this;
    }

    /**
     * 注册一个命名颜色到调色板，使用 0xAARRGGBB 整数值
     *
     * @param name 颜色名
     * @param argb 0xAARRGGBB 格式颜色值
     * @return 当前画布，便于链式调用
     */
    public PixelCanvas color(String name, int argb) {
        palette.put(name, argb);
        return this;
    }

    /**
     * 取命名颜色的 ARGB 值，未注册时抛异常以便尽早发现拼写错误
     *
     * @param name 颜色名
     * @return 0xAARRGGBB 颜色值
     */
    public int c(String name) {
        Integer v = palette.get(name);
        if (v == null) {
            throw new IllegalArgumentException("调色板中不存在颜色 " + name);
        }
        return v;
    }

    /**
     * 判断坐标是否在画布范围内
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 越界返回 false
     */
    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /**
     * 设置单个像素，越界坐标静默忽略（贴图边缘裁剪友好）
     *
     * @param x    横坐标
     * @param y    纵坐标
     * @param argb 0xAARRGGBB 颜色值
     */
    public void set(int x, int y, int argb) {
        if (inBounds(x, y)) {
            pixels[y * width + x] = argb;
        }
    }

    /**
     * 用调色板颜色名设置单个像素
     *
     * @param x    横坐标
     * @param y    纵坐标
     * @param name 调色板颜色名
     */
    public void set(int x, int y, String name) {
        set(x, y, c(name));
    }

    /**
     * 读取指定像素的 ARGB 值，越界返回 0
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 0xAARRGGBB 颜色值
     */
    public int get(int x, int y) {
        return inBounds(x, y) ? pixels[y * width + x] : 0;
    }

    /**
     * 画布宽度（像素）
     *
     * @return 宽度
     */
    public int width() {
        return width;
    }

    /**
     * 画布高度（像素）
     *
     * @return 高度
     */
    public int height() {
        return height;
    }

    /**
     * 将整块画布填充为指定颜色
     *
     * @param name 调色板颜色名
     */
    public void fill(String name) {
        int argb = c(name);
        java.util.Arrays.fill(pixels, argb);
    }

    /**
     * 画实心矩形，含两端点坐标（闭区间）
     *
     * @param x0   左上角横坐标
     * @param y0   左上角纵坐标
     * @param x1   右下角横坐标
     * @param y1   右下角纵坐标
     * @param name 调色板颜色名
     */
    public void rect(int x0, int y0, int x1, int y1, String name) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, name);
            }
        }
    }

    /**
     * 画一像素宽的空心矩形描边，含两端点坐标
     *
     * @param x0   左上角横坐标
     * @param y0   左上角纵坐标
     * @param x1   右下角横坐标
     * @param y1   右下角纵坐标
     * @param name 调色板颜色名
     */
    public void outline(int x0, int y0, int x1, int y1, String name) {
        hline(x0, x1, y0, name);
        hline(x0, x1, y1, name);
        vline(y0, y1, x0, name);
        vline(y0, y1, x1, name);
    }

    /**
     * 画水平线段，含两端点
     *
     * @param x0   起点横坐标
     * @param x1   终点横坐标
     * @param y    纵坐标
     * @param name 调色板颜色名
     */
    public void hline(int x0, int x1, int y, String name) {
        int from = Math.min(x0, x1);
        int to = Math.max(x0, x1);
        for (int x = from; x <= to; x++) {
            set(x, y, name);
        }
    }

    /**
     * 画垂直线段，含两端点
     *
     * @param y0   起点纵坐标
     * @param y1   终点纵坐标
     * @param x    横坐标
     * @param name 调色板颜色名
     */
    public void vline(int y0, int y1, int x, String name) {
        int from = Math.min(y0, y1);
        int to = Math.max(y0, y1);
        for (int y = from; y <= to; y++) {
            set(x, y, name);
        }
    }

    /**
     * 随机散布噪点，用于金属颗粒感等表面质感
     *
     * @param name    调色板颜色名
     * @param density 噪点密度，0 到 1 之间，每个像素独立概率
     * @param seed    随机种子，保证确定性（同一贴图每次生成结果一致）
     */
    public void noise(String name, double density, long seed) {
        Random rnd = new Random(seed);
        int argb = c(name);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (rnd.nextDouble() < density) {
                    set(x, y, argb);
                }
            }
        }
    }

    /**
     * 将画布保存为原始尺寸 PNG
     *
     * @param path 输出文件路径，父目录不存在时自动创建
     */
    public void save(String path) throws IOException {
        BufferedImage img = toImage();
        writePng(img, path);
    }

    /**
     * 将画布最近邻放大后保存为预览 PNG，便于肉眼观察与视觉模型审查
     *
     * @param path  输出文件路径
     * @param scale 放大倍数，例如 8 表示 16x16 贴图放大为 128x128
     */
    public void savePreview(String path, int scale) throws IOException {
        BufferedImage img = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                g.setColor(new Color(pixels[y * width + x], true));
                g.fillRect(x * scale, y * scale, scale, scale);
            }
        }
        g.dispose();
        writePng(img, path);
    }

    /**
     * 将多张同尺寸画布垂直堆叠合成一张 PNG
     * <p>
     * MC 材质动画帧格式：每帧等尺寸，从上到下垂直排列，配合 .mcmeta 声明轮播
     * （如 4 帧 32x32 合成 32x128）；帧数需整除高度（本方法不做校验，调用方保证）
     *
     * @param frames 帧画布列表（非空，且尺寸必须全部一致）
     * @param path   输出文件路径，父目录不存在时自动创建
     */
    public static void stackVertical(List<PixelCanvas> frames, String path) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("帧列表不能为空");
        }
        int w = frames.get(0).width;
        int h = frames.get(0).height;
        BufferedImage img = new BufferedImage(w, h * frames.size(), BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames.size(); f++) {
            PixelCanvas frame = frames.get(f);
            if (frame.width != w || frame.height != h) {
                throw new IllegalArgumentException("帧尺寸不一致: 第 " + f + " 帧 " + frame.width + "x" + frame.height);
            }
            img.setRGB(0, f * h, w, h, frame.pixels, 0, w);
        }
        writePng(img, path);
    }

    /**
     * 写出 MC 材质动画声明文件（与动画贴图同路径的 .png.mcmeta）
     * <p>
     * 声明 anim 块：frametime 为每帧停留的 tick 数（20 tick = 1 秒），
     * interpolate 为 true 时游戏在帧间做颜色插值（流动/脉动效果更平滑）
     *
     * @param path          动画贴图完整路径（自动追加 .mcmeta 后缀，调用方传贴图路径即可）
     * @param frametime     每帧 tick 数
     * @param interpolate   是否启用帧间插值
     */
    public static void writeMcmeta(String path, int frametime, boolean interpolate) throws IOException {
        String json = "{\"animation\":{\"frametime\":" + frametime + ",\"interpolate\":" + interpolate + "}}";
        Files.write(Paths.get(path + ".mcmeta"), json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将像素数组转为 BufferedImage
     *
     * @return TYPE_INT_ARGB 图像
     */
    private BufferedImage toImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }

    /**
     * 将图像写入 PNG 文件，自动创建父目录
     *
     * @param img  BufferedImage 图像
     * @param path 输出文件路径
     */
    private static void writePng(BufferedImage img, String path) throws IOException {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(img, "png", file);
    }
}
