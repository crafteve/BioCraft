// EnzymeMachineScript.java
// 酶工厂分层贴图生成脚本（静态版）：外壳 5 面 64x64 + 内容层 5 张小贴图
// 分层架构（与模型 JSON 配套，像素拼接方案）：
//   外壳层：5 张 64x64 全不透明贴图（灰金属机身 + 铆钉 + 凹槽 + 固定部件），无染色
//   内容层：5 张小贴图（观察窗液体/铭牌底/按钮/管道/舱口芯），灰阶明暗，
//           运行时 tint 乘法染成类别主题色；模型面片在方块外侧凸出 0.01~0.04 覆盖外壳
// 设计要点：
//   - 无透明像素依赖：MC 方块模型走 solid 渲染通道，透明像素行为不可靠
//     （曾致"叠加失败黑成一片"——透过透明窗看到方块内部），像素拼接是可靠方案
//   - 灰阶取亮区（150~240）：染色后 = 主题色 x 0.6~0.95，亮且饱和（用户要求）
//   - 静态贴图：动画帧堆叠/mcmeta 基础设施已就绪，动画另期再做
// 编译：javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/*.java
// 运行：java -cp tools/texturegen/out EnzymeMachineScript
// 输出：tools/texturegen/output/enzyme/（原始图 + 8x 预览 + 染色模拟预览）

import java.io.IOException;

public class EnzymeMachineScript {

    /** 输出根目录 */
    private static final String OUT_DIR = "tools/texturegen/output/enzyme";

    /**
     * 程序入口：绘制外壳 5 面 + 内容层 5 张 + 全部预览
     *
     * @param args 未使用
     */
    public static void main(String[] args) throws IOException {
        save(shellFront(), "shell_front");
        save(shellSide(), "shell_side");
        save(shellBack(), "shell_back");
        save(shellTop(), "shell_top");
        save(shellBottom(), "shell_bottom");

        save(layerWindow(), "layer_window");
        save(layerNameplate(), "layer_nameplate");
        save(layerButton(), "layer_button");
        save(layerPipe(), "layer_pipe");
        save(layerPort(), "layer_port");

        // 染色模拟预览（与游戏内 tint 乘法一致），供自查亮度/饱和度
        int[] themes = {0x6FC3DF, 0xFFA94D, 0xB57EDC, 0xFFD966};
        String[] tags = {"ec1", "ec2", "ec4", "ec5"};
        for (int i = 0; i < themes.length; i++) {
            save(tint(layerWindow(), themes[i]), "window_tinted_" + tags[i] + "_preview");
            save(tint(layerNameplate(), themes[i]), "nameplate_tinted_" + tags[i] + "_preview");
            save(tint(layerButton(), themes[i]), "button_tinted_" + tags[i] + "_preview");
            save(tint(layerPipe(), themes[i]), "pipe_tinted_" + tags[i] + "_preview");
            save(tint(layerPort(), themes[i]), "port_tinted_" + tags[i] + "_preview");
        }
        System.out.println("酶工厂分层贴图生成完成 -> " + OUT_DIR);
    }

    /**
     * 保存贴图原始图 + 8x 预览
     *
     * @param canvas 画布
     * @param name   文件名（不含扩展名）
     */
    private static void save(PixelCanvas canvas, String name) throws IOException {
        canvas.save(OUT_DIR + "/" + name + ".png");
        canvas.savePreview(OUT_DIR + "/" + name + "_preview.png", 8);
    }

    /**
     * 绘制外壳正面（64x64，全不透明）
     * <p>
     * 布局（自上而下）：凹槽面板（8..55 矩形）→ 四角铆钉（凹槽外）→
     * 顶部铭牌框（金属框，内容层盖内）→ 中央观察窗框（双层，内容层盖内）→
     * 右侧绿色指示灯与下方按钮占位（按钮由内容层染色盖住）→
     * 凹槽外底部左侧双插槽 + 右侧通风口
     *
     * @return 64x64 画布
     */
    private static PixelCanvas shellFront() {
        PixelCanvas p = baseShell();
        // 顶部铭牌框：金属外框 + 深灰内底（内容层盖 x20..44 y10..16）
        p.rect(18, 8, 46, 18, "metal");
        p.rect(19, 9, 45, 17, "groove");
        // 中央观察窗框：凹槽外框 + 金属内框（内容层盖 x12..40 y28..48）
        p.outline(10, 26, 42, 50, "groove");
        p.outline(12, 28, 40, 48, "metal");
        // 窗框内底色（被内容层完全覆盖，画灰防穿帮）
        p.rect(13, 29, 39, 47, "bodyDark");
        // 右侧绿色指示灯（固定色，外壳层）
        p.rect(46, 26, 54, 34, "groove");
        p.rect(48, 28, 52, 32, "ledGreen");
        p.rect(48, 28, 49, 29, "ledHot");
        // 按钮占位底（内容层盖 x46..54 y40..48）
        p.rect(46, 40, 54, 48, "bodyDark");
        // 凹槽外底部：左侧双插槽（黑色内凹）
        p.rect(12, 57, 20, 61, "slotBlack");
        p.rect(24, 57, 32, 61, "slotBlack");
        p.hline(12, 20, 57, "bodyDarkest");
        p.hline(24, 32, 57, "bodyDarkest");
        // 右侧横向通风口：凹底 + 三组金属竖条
        p.rect(36, 57, 58, 61, "groove");
        p.rect(38, 57, 40, 61, "metal");
        p.rect(44, 57, 46, 61, "metal");
        p.rect(50, 57, 52, 61, "metal");
        p.rect(56, 57, 58, 61, "metal");
        return p;
    }

    /**
     * 绘制外壳侧面（64x64，全不透明）
     * <p>
     * 布局：凹槽面板 + 铆钉 + 中央贯穿管道区域（内容层盖 x20..44 y8..56，
     * 底色画灰）+ 管道上下端金属法兰 + 右侧接口面板 + 左下散热条
     *
     * @return 64x64 画布
     */
    private static PixelCanvas shellSide() {
        PixelCanvas p = baseShell();
        // 管道占位底（内容层盖 x20..44 y8..56）
        p.rect(20, 8, 44, 56, "bodyDark");
        // 上下端法兰：金属环 + 凹槽内圈
        p.rect(16, 4, 48, 10, "metal");
        p.rect(18, 5, 46, 9, "groove");
        p.rect(16, 54, 48, 60, "metal");
        p.rect(18, 55, 46, 59, "groove");
        // 右侧接口面板：凹底 + 三个圆点细节
        p.rect(47, 14, 53, 28, "groove");
        p.set(50, 17, "metal");
        p.set(50, 21, "metal");
        p.set(50, 25, "metal");
        // 左下散热条：三条横向凹条
        p.rect(8, 38, 16, 40, "groove");
        p.rect(8, 44, 16, 46, "groove");
        p.rect(8, 50, 16, 52, "groove");
        return p;
    }

    /**
     * 绘制外壳背面（64x64，全不透明，无内容层）
     * <p>
     * 布局：凹槽面板 + 铆钉 + 中央大型散热格栅（凹底 + 六条金属竖条）+
     * 底部横向通风口
     *
     * @return 64x64 画布
     */
    private static PixelCanvas shellBack() {
        PixelCanvas p = baseShell();
        // 中央散热格栅
        p.rect(18, 12, 46, 52, "groove");
        for (int x = 20; x <= 44; x += 4) {
            p.rect(x, 12, x + 2, 52, "metal");
        }
        // 底部横向通风口
        p.rect(18, 56, 46, 61, "groove");
        for (int x = 20; x <= 44; x += 4) {
            p.rect(x, 56, x + 2, 61, "metal");
        }
        return p;
    }

    /**
     * 绘制外壳顶面（64x64，全不透明）
     * <p>
     * 布局：凹槽面板 + 铆钉 + 四角金属接口（带凹槽内芯）+
     * 中央舱口金属环（内容层盖 x24..40 y24..40）
     *
     * @return 64x64 画布
     */
    private static PixelCanvas shellTop() {
        PixelCanvas p = baseShell();
        // 四角接口：金属框 + 凹槽内芯
        for (int[] pos : new int[][]{{8, 8}, {48, 8}, {8, 48}, {48, 48}}) {
            p.rect(pos[0], pos[1], pos[0] + 8, pos[1] + 8, "metal");
            p.rect(pos[0] + 1, pos[1] + 1, pos[0] + 7, pos[1] + 7, "groove");
        }
        // 中央舱口环：金属外环 + 凹槽内环（内容层盖 x24..40 y24..40）
        p.rect(18, 18, 46, 46, "metal");
        p.rect(20, 20, 44, 44, "groove");
        // 舱口芯占位底
        p.rect(24, 24, 40, 40, "bodyDark");
        return p;
    }

    /**
     * 绘制外壳底面（64x64，全不透明，无内容层）
     * <p>
     * 布局：凹槽面板 + 铆钉 + 中央十字底座加强筋 + 四角固定孔
     *
     * @return 64x64 画布
     */
    private static PixelCanvas shellBottom() {
        PixelCanvas p = baseShell();
        // 中央十字加强筋
        p.rect(30, 8, 34, 56, "metal");
        p.rect(8, 30, 56, 34, "metal");
        p.rect(32, 30, 33, 33, "body");
        // 四角固定孔
        for (int[] pos : new int[][]{{10, 10}, {50, 10}, {10, 50}, {50, 50}}) {
            p.rect(pos[0], pos[1], pos[0] + 2, pos[1] + 2, "slotBlack");
        }
        return p;
    }

    /**
     * 外壳公共底座：机身四档渐变 + 外框 + 凹槽面板 + 四角铆钉
     * <p>
     * 渐变（左上光源惯例）：上/左 6px 亮、下/右 6px 暗，逐像素插值平滑过渡
     *
     * @return 已铺好底座的 64x64 画布
     */
    private static PixelCanvas baseShell() {
        PixelCanvas p = new PixelCanvas(64, 64);
        p.color("outline", "#1C1C24");
        p.color("bodyLightest", "#C6C6CC");
        p.color("bodyLight", "#ACACB2");
        p.color("body", "#8A8A90");
        p.color("bodyDark", "#6A6A70");
        p.color("bodyDarkest", "#52525A");
        p.color("groove", "#33333B");
        p.color("metal", "#B8B8BE");
        p.color("slotBlack", "#1E1E23");
        p.color("ledGreen", "#4ADE80");
        p.color("ledHot", "#DFFFE8");

        p.fill("body");
        // 边缘渐变：上/左 8px 亮、下/右 8px 暗（逐像素插值，四档过渡）
        gradientEdge(p, 0); // 上边
        gradientEdge(p, 1); // 左边
        gradientEdge(p, 2); // 下边
        gradientEdge(p, 3); // 右边
        p.outline(0, 0, 63, 63, "outline");
        // 凹槽面板（8..55 矩形凹线）
        p.outline(8, 8, 55, 55, "groove");
        // 四角铆钉（3x3 对角渐变：左上高光右下暗）
        rivet(p, 5, 5);
        rivet(p, 56, 5);
        rivet(p, 5, 56);
        rivet(p, 56, 56);
        return p;
    }

    /**
     * 绘制一条边缘渐变带（8px 宽，四档过渡色）
     * <p>
     * 亮边（上/左）从外到内：最亮 -> 亮 -> 主体 -> 暗；
     * 暗边（下/右）从外到内：最暗 -> 暗 -> 主体 -> 亮
     *
     * @param p    目标画布
     * @param side 0=上边 1=左边 2=下边 3=右边
     */
    private static void gradientEdge(PixelCanvas p, int side) {
        int lightest = p.c("bodyLightest");
        int light = p.c("bodyLight");
        int body = p.c("body");
        int dark = p.c("bodyDark");
        int darkest = p.c("bodyDarkest");
        for (int i = 1; i <= 8; i++) {
            int lightColor = i <= 2 ? lightest : (i <= 4 ? light : (i <= 6 ? body : dark));
            int darkColor = i <= 2 ? darkest : (i <= 4 ? dark : (i <= 6 ? body : light));
            int color = (side == 0 || side == 1) ? lightColor : darkColor;
            if (side == 0 || side == 2) {
                int y = side == 0 ? i : 64 - i;
                for (int x = 0; x < 64; x++) {
                    p.set(x, y, color);
                }
            } else {
                int x = side == 1 ? i : 64 - i;
                for (int y = 0; y < 64; y++) {
                    p.set(x, y, color);
                }
            }
        }
    }

    /**
     * 画单个铆钉（3x3 对角渐变：左上高光、右下暗）
     *
     * @param p 目标画布
     * @param x 铆钉左上角横坐标
     * @param y 铆钉左上角纵坐标
     */
    private static void rivet(PixelCanvas p, int x, int y) {
        p.rect(x, y, x + 2, y + 2, "bodyLight");
        p.set(x, y, "bodyLightest");
        p.set(x + 2, y + 2, "bodyDarkest");
        p.set(x + 2, y, "body");
        p.set(x, y + 2, "body");
    }

    /**
     * 绘制观察窗内容层（29x21 灰阶：液面高光 + 液体渐变 + 三颗上浮粒子）
     * <p>
     * 与外壳窗框内区域精确对应（x12..40 y28..48），全部不透明；
     * 灰阶 150~240 保证染色后亮而饱和
     *
     * @return 29x21 画布
     */
    private static PixelCanvas layerWindow() {
        PixelCanvas p = new PixelCanvas(29, 21);
        p.color("gLow", "#8F8F9C");
        p.color("gMid", "#B8B8C4");
        p.color("gHigh", "#E0E0E8");
        p.color("gTop", "#F2F2F7");
        p.color("gParticle", "#FFFFFF");
        // 液面高光（顶部 2px）+ 渐入主体 + 底部深色
        p.rect(0, 0, 28, 1, "gTop");
        p.rect(0, 2, 28, 5, "gHigh");
        p.rect(0, 6, 28, 12, "gMid");
        p.rect(0, 13, 28, 20, "gLow");
        // 液体内部横向亮带（中部，模拟光照）
        p.hline(4, 24, 9, "gHigh");
        // 三颗上浮粒子（静态位置）
        p.set(7, 5, "gParticle");
        p.set(13, 3, "gParticle");
        p.set(21, 8, "gParticle");
        // 底部微气泡
        p.set(5, 17, "gHigh");
        p.set(16, 19, "gHigh");
        return p;
    }

    /**
     * 绘制铭牌内容层（25x7 灰阶亮底：顶部高光渐变，BER 文字叠画其上）
     * <p>
     * 与外壳铭牌框内区域精确对应（x20..44 y10..16）；染色后为亮主题色，
     * BER 用主题色压暗 35% 的深色文字形成对比
     *
     * @return 25x7 画布
     */
    private static PixelCanvas layerNameplate() {
        PixelCanvas p = new PixelCanvas(25, 7);
        p.color("gTop", "#E8E8EE");
        p.color("gMid", "#D0D0D8");
        p.color("gLow", "#B0B0B8");
        p.rect(0, 0, 24, 0, "gTop");
        p.rect(0, 1, 24, 3, "gMid");
        p.rect(0, 4, 24, 6, "gLow");
        return p;
    }

    /**
     * 绘制按钮内容层（9x9 灰阶：顶部高光渐变凸起按钮）
     * <p>
     * 与外壳按钮占位区域精确对应（x46..54 y40..48）；染色后呈主题色按钮
     *
     * @return 9x9 画布
     */
    private static PixelCanvas layerButton() {
        PixelCanvas p = new PixelCanvas(9, 9);
        p.color("gHigh", "#E8E8EE");
        p.color("gMid", "#C8C8D0");
        p.color("gLow", "#A0A0A8");
        p.color("gWhite", "#FFFFFF");
        p.rect(0, 0, 8, 1, "gHigh");
        p.rect(0, 2, 8, 6, "gMid");
        p.rect(0, 7, 8, 8, "gLow");
        p.set(1, 1, "gWhite");
        return p;
    }

    /**
     * 绘制管道内容层（25x49 灰阶：竖直液体渐变 + 中央亮带）
     * <p>
     * 与外壳侧面管道占位区域精确对应（x20..44 y8..56）；
     * 两端贴合法兰内侧
     *
     * @return 25x49 画布
     */
    private static PixelCanvas layerPipe() {
        PixelCanvas p = new PixelCanvas(25, 49);
        p.color("gLow", "#8F8F9C");
        p.color("gMid", "#C0C0CC");
        p.color("gHigh", "#E8E8F0");
        p.color("gCore", "#F5F5FA");
        p.rect(0, 0, 24, 48, "gMid");
        p.rect(3, 0, 21, 48, "gHigh");
        p.rect(8, 0, 16, 48, "gCore");
        p.rect(0, 44, 24, 48, "gLow");
        // 上下端液面折光
        p.rect(3, 0, 21, 1, "gCore");
        return p;
    }

    /**
     * 绘制舱口芯内容层（17x17 灰阶：同心环径向渐变，中心亮）
     * <p>
     * 与外壳顶面舱口占位区域精确对应（x24..40 y24..40）
     *
     * @return 17x17 画布
     */
    private static PixelCanvas layerPort() {
        PixelCanvas p = new PixelCanvas(17, 17);
        p.color("gLow", "#8F8F9C");
        p.color("gMid", "#B8B8C4");
        p.color("gHigh", "#DCDCE4");
        p.color("gCore", "#F2F2F7");
        p.rect(0, 0, 16, 16, "gMid");
        p.rect(2, 2, 14, 14, "gHigh");
        p.rect(5, 5, 11, 11, "gCore");
        p.color("gWhite", "#FFFFFF");
        p.set(8, 8, "gWhite");
        return p;
    }

    /**
     * 染色模拟：灰阶图像乘主题色（与游戏内 BlockColors tint 乘法一致）
     *
     * @param gray  灰阶画布
     * @param theme 主题色 0xRRGGBB
     * @return 染色后的画布
     */
    private static PixelCanvas tint(PixelCanvas gray, int theme) {
        PixelCanvas out = new PixelCanvas(gray.width(), gray.height());
        int tr = (theme >> 16) & 0xFF;
        int tg = (theme >> 8) & 0xFF;
        int tb = theme & 0xFF;
        for (int y = 0; y < gray.height(); y++) {
            for (int x = 0; x < gray.width(); x++) {
                int argb = gray.get(x, y);
                int a = (argb >>> 24) & 0xFF;
                int l = argb & 0xFF;
                out.set(x, y, (a << 24) | ((l * tr / 255) << 16) | ((l * tg / 255) << 8) | (l * tb / 255));
            }
        }
        return out;
    }
}
