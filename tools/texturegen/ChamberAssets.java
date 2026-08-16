// ChamberAssets.java
// 酶容器方块概念设计稿生成器（Phase 1）
// 生成内容：六面 16×16 原生贴图 + 每面 8 倍预览 + 一张展开图（顶/正/背/左/底/右）
// 设计依据：《酶容器方块概念设计_2026-08-16.md》
// 关键约定：主题色槽位只画灰度（白=纯色、灰=暗化、黑=不可见），
//   正式渲染阶段由 BlockColor 把酶主题色乘上去，得到"带内置左上光照的主题色"
// 编译：javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/*.java
// 运行：java -cp tools/texturegen/out ChamberAssets
// 输出目录 tools/texturegen/output/concept（gitignore，概念稿不入库，脚本可复现）

import java.io.IOException;

public class ChamberAssets {

    // 中性固定调色板（六面共享，见设计文档 2.1）
    static final int OUTLINE = 0xFF20242B;      // 1px 外轮廓
    static final int METAL_DARK = 0xFF39404B;   // 金属暗部（背光边/法兰底）
    static final int METAL = 0xFF4E5663;        // 金属基色（机身主体）
    static final int METAL_LIGHT = 0xFF66707F;  // 金属亮面（受光边）
    static final int METAL_HI = 0xFF8B96A6;     // 金属高光（受光边/铭牌条）
    static final int FRAME_DARK = 0xFF1D2129;   // 深色结构件（窗框/凹槽/格栅/支撑脚）
    static final int GLASS = 0xFF0C0F14;        // 玻璃底（观察窗内深色）
    static final int GLASS_HI = 0xFF2A3240;     // 玻璃高光（窗内侧左上）
    static final int LIQ_HI = 0xFFFFFFFF;       // 液体受光（主题槽位灰度最亮）
    static final int LIQ = 0xFFC8C8C8;          // 液体基色（主题槽位灰度）
    static final int LIQ_DARK = 0xFF808080;     // 液体背光（主题槽位灰度暗部）
    static final int LAMP = 0xFFE8E8E8;         // 指示灯灰度（左上 1px 更亮）

    /**
     * 程序入口：按模式生成资源
     * <ul>
     *   <li>concept（默认）：六面概念稿 + 每面预览 + 展开图 → output/concept/</li>
     *   <li>textures：正式反照率贴图（base 5 张 + side 镜像 1 张 + theme 6 张 + lamp 1 张）
     *       → output/block/，确定后手动拷入 src/main/resources/assets/biocraft/textures/block/</li>
     * </ul>
     *
     * @param args 可选模式参数 concept/textures
     */
    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "concept";
        if ("textures".equals(mode)) {
            textures();
        } else {
            concept();
        }
    }

    /**
     * 概念稿模式：六面贴图 + 每面 8 倍预览 + 展开图
     *
     * @throws IOException PNG 写出失败时抛出
     */
    static void concept() throws IOException {
        String out = "tools/texturegen/output/concept";
        String[] names = {"front", "side", "back", "top", "bottom"};
        PixelCanvas[] faces = {front(), side(), back(), top(), bottom()};
        for (int i = 0; i < faces.length; i++) {
            faces[i].save(out + "/chamber_" + names[i] + ".png");
            faces[i].savePreview(out + "/chamber_" + names[i] + "_preview_8x.png", 8);
        }
        unfolded();
        System.out.println("概念稿生成完成: " + out);
    }

    /**
     * 六面共享的机身骨架：填充基色 + 1px 外轮廓 + 左上光照 + 四角螺栓
     * <p>
     * 光照方向统一左上（顶边/左边受光、底边/右边压暗），
     * 顶面（topFace=true）受光更亮一档——MC 模型面着色会让顶面天然最亮，
     * 纹理内光照与之叠加后层次更强
     *
     * @param fill    机身基色（顶面用 METAL，底面用 METAL_DARK，其余 METAL）
     * @param topFace 是否为顶面（顶面受光高光一档）
     * @return 已铺好骨架的 16×16 画布
     */
    static PixelCanvas scaffold(int fill, boolean topFace) {
        PixelCanvas c = new PixelCanvas(16, 16);
        c.fill(fill);
        c.outline(0, 0, 15, 15, OUTLINE);
        c.hline(1, 14, 1, topFace ? METAL_HI : METAL_LIGHT);
        c.vline(1, 14, 1, topFace ? METAL_HI : METAL_LIGHT);
        c.hline(1, 14, 14, METAL_DARK);
        c.vline(1, 14, 14, METAL_DARK);
        bolts(c);
        return c;
    }

    /**
     * 四角 2×2 螺栓（六面统一位置 1,1 / 13,1 / 1,13 / 13,13），构成定位基准
     *
     * @param c 目标画布
     */
    static void bolts(PixelCanvas c) {
        bolt(c, 1, 1);
        bolt(c, 13, 1);
        bolt(c, 1, 13);
        bolt(c, 13, 13);
    }

    /**
     * 单个 2×2 螺栓：暗底 + 左上高光点
     *
     * @param c 目标画布
     * @param x 螺栓左上角横坐标
     * @param y 螺栓左上角纵坐标
     */
    static void bolt(PixelCanvas c, int x, int y) {
        c.rect(x, y, x + 1, y + 1, METAL_DARK);
        c.set(x, y, METAL_HI);
    }

    /**
     * 2×2 指示灯（主题槽位，灰度）：左上角 1px 最亮模拟灯芯高光
     * <p>
     * 侧面/背面小灯用（正面灯已放大为 4×4 lampBig，见 front）
     *
     * @param c 目标画布
     * @param x 灯左上角横坐标
     * @param y 灯左上角纵坐标
     */
    static void lamp(PixelCanvas c, int x, int y) {
        c.rect(x, y, x + 1, y + 1, LAMP);
        c.set(x, y, 0xFFFFFFFF);
    }

    /**
     * 2×2 金属接口环：暗底 + 左上高光 + 右下内孔暗点
     *
     * @param c 目标画布
     * @param x 环左上角横坐标
     * @param y 环左上角纵坐标
     */
    static void ring(PixelCanvas c, int x, int y) {
        c.rect(x, y, x + 1, y + 1, METAL_DARK);
        c.set(x, y, METAL_LIGHT);
        c.set(x + 1, y + 1, FRAME_DARK);
    }

    /**
     * 正面：凸字形观察窗（主要主题色载体）+ 两侧 2×2 指示灯 + 底部双接口环 + 中央铭牌条
     * <p>
     * 布局按用户新设计（2026-08-16 实测反馈重绘）：
     * 指示灯 2×2 at (3,4)-(4,5) 与 (11,4)-(12,5)（窗口横条两侧，状态灯：
     * 黄=等料/红=停摆/绿=运行，由方块实体状态通道染色）；
     * 窗口为凸字形——上横条 (6,4)-(9,6) + 下主体 (3,7)-(12,10)，
     * 玻璃底 + 液体各按上亮下暗渐变
     *
     * @return 正面 16×16 画布
     */
    static PixelCanvas front() {
        PixelCanvas c = scaffold(METAL, false);
        // 中央铭牌条：两端暗点 + 高光横条（品牌条带感）
        c.set(5, 2, FRAME_DARK);
        c.set(10, 2, FRAME_DARK);
        c.hline(6, 9, 2, METAL_HI);
        // 凸字形窗口：先玻璃后外框（框线盖在玻璃边缘）
        // 上横条 (6,4)-(9,6)，外框 (5,3)-(10,7)
        // 下主体 (3,7)-(12,10)，外框 (2,6)-(13,11)——两框肩部重叠成凸字轮廓
        c.rect(6, 4, 9, 6, GLASS);
        c.rect(3, 7, 12, 10, GLASS);
        c.outline(2, 6, 13, 11, FRAME_DARK);
        c.outline(5, 3, 10, 7, FRAME_DARK);
        // 玻璃内侧左上高光（上横条与下主体各一组）
        c.hline(7, 8, 4, GLASS_HI);
        c.vline(4, 6, 6, GLASS_HI);
        c.hline(4, 11, 7, GLASS_HI);
        c.vline(7, 10, 3, GLASS_HI);
        // 窗内液体（凸字两段，顶行受光、底行背光）
        windowLiquidContent(c, 6, 4, 9, 6);
        windowLiquidContent(c, 3, 7, 12, 10);
        // 两侧指示灯 2×2（用户指定位置，状态灯由 tint 承载三态色）
        lamp(c, 3, 4);
        lamp(c, 11, 4);
        // 底部双接口环
        ring(c, 4, 13);
        ring(c, 10, 13);
        return c;
    }

    /**
     * 液体内容填充：矩形区域内按"顶行受光、底行背光"逐行渐变
     * <p>
     * 概念稿与 theme 贴图共用（内容坐标 = UV 裁剪区，采样 1:1）
     *
     * @param c  目标画布
     * @param x0 矩形左上角横坐标
     * @param y0 矩形左上角纵坐标
     * @param x1 矩形右下角横坐标
     * @param y1 矩形右下角纵坐标
     */
    static void windowLiquidContent(PixelCanvas c, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            int col = y == y0 ? 0xFFFFFFFF : (y == y1 ? LIQ_DARK : LIQ);
            for (int x = x0; x <= x1; x++) {
                c.set(x, y, col);
            }
        }
    }

    /**
     * 侧面（左右共用同一贴图）：竖直主管道（外壁 2px + 内液体 1px 全高）
     * + 上下法兰 + 小观察孔 + 状态灯
     * <p>
     * 管道内液体即"主题色液体管"，上输入/下输出法兰传达"可接入生产线"，
     * 小观察孔 3×3 位于 y6..8，与正面窗口中段同高形成结构呼应
     *
     * @return 侧面 16×16 画布
     */
    static PixelCanvas side() {
        PixelCanvas c = scaffold(METAL, false);
        // 顶部输入法兰与底部输出法兰
        c.rect(2, 0, 6, 1, METAL);
        c.hline(2, 6, 0, METAL_LIGHT);
        c.rect(2, 14, 6, 15, METAL);
        c.hline(2, 6, 15, METAL_DARK);
        // 竖直主管道：x3/x5 管壁、x4 管内液体（上亮下暗）
        c.vline(2, 13, 3, METAL_DARK);
        c.vline(2, 13, 5, METAL_DARK);
        for (int y = 2; y <= 13; y++) {
            c.set(4, y, y <= 6 ? LIQ_HI : (y <= 10 ? LIQ : LIQ_DARK));
        }
        // 短横管：主管道连向观察孔（高光上缘）
        c.hline(6, 8, 7, METAL_DARK);
        c.hline(6, 8, 6, METAL_LIGHT);
        // 小观察孔：5×5 外圈 + 玻璃底 + 3×3 液体（主题槽位）
        c.outline(9, 5, 13, 9, FRAME_DARK);
        c.rect(10, 6, 12, 8, LIQ);
        c.set(10, 6, 0xFFFFFFFF);
        // 右下状态灯
        lamp(c, 11, 12);
        return c;
    }

    /**
     * 背面：排气格栅 + 中央大法兰（内环液体）+ 维护面板（四角螺钉）+ 状态灯
     * <p>
     * 背面比正面设备化：大法兰是"接入生产线"的主接口，
     * 维护面板用内凹矩形 + 螺钉点表达可维修性
     *
     * @return 背面 16×16 画布
     */
    static PixelCanvas back() {
        PixelCanvas c = scaffold(METAL, false);
        // 顶部排气格栅：vent 色块 + 上缘受光亮边（内凹 + 左上光照语言）
        c.rect(3, 2, 11, 3, FRAME_DARK);
        c.hline(3, 11, 2, METAL_LIGHT);
        // 中央大法兰 6×4：暗底 + 左上高光 + 内环槽
        c.rect(4, 6, 11, 9, METAL_DARK);
        c.hline(5, 10, 6, METAL_LIGHT);
        c.vline(6, 9, 4, METAL_LIGHT);
        c.rect(5, 7, 10, 8, FRAME_DARK);
        // 内环液体 4×2（主题槽位）
        c.rect(6, 7, 9, 8, LIQ);
        c.set(6, 7, 0xFFFFFFFF);
        // 维护面板：内凹矩形 + 上高光 + 四角螺钉点
        c.rect(2, 11, 10, 13, METAL_DARK);
        c.hline(3, 9, 11, METAL_LIGHT);
        c.hline(3, 9, 13, METAL);
        c.set(3, 11, METAL_HI);
        c.set(9, 11, METAL_HI);
        c.set(3, 13, METAL_HI);
        c.set(9, 13, METAL_HI);
        // 右下状态灯
        lamp(c, 11, 12);
        return c;
    }

    /**
     * 顶面：中央观察孔（俯视辨识主力）+ 右上小型接口
     * <p>
     * 顶面是玩家俯视观察的主面，观察孔 8×8 外圈 + 玻璃 6×6 + 液体 4×4
     * （顶面受光最强，液体灰度整体偏亮），不同酶从上方一眼可辨
     *
     * @return 顶面 16×16 画布
     */
    static PixelCanvas top() {
        PixelCanvas c = scaffold(METAL, true);
        // 观察孔：外圈 + 玻璃底 + 内侧左上高光
        c.outline(4, 4, 11, 11, FRAME_DARK);
        c.rect(5, 5, 10, 10, GLASS);
        c.hline(6, 9, 5, GLASS_HI);
        c.vline(5, 10, 5, GLASS_HI);
        // 中央液体 4×4（主题槽位，受光→背光）
        c.hline(6, 9, 6, 0xFFFFFFFF);
        c.hline(6, 9, 7, LIQ_HI);
        c.hline(6, 9, 8, LIQ);
        c.hline(6, 9, 9, LIQ_DARK);
        // 右上小型接口环
        ring(c, 12, 3);
        return c;
    }

    /**
     * 底面：金属底座（整体最暗）+ 四角支撑脚 + 中央十字管线 + 中央接口环
     * <p>
     * 底面不承担辨识任务，只留一个 2×2 主题接口环与其他面呼应；
     * 支撑脚为 frameDark 2×2 + 左上高光，管线用十字表达"底部走线"
     *
     * @return 底面 16×16 画布
     */
    static PixelCanvas bottom() {
        PixelCanvas c = scaffold(METAL_DARK, false);
        // 中央十字管线（先画，中央环后画覆盖中心）
        c.hline(2, 13, 7, METAL);
        c.vline(2, 13, 7, METAL);
        c.hline(2, 13, 8, METAL_DARK);
        // 中央接口环（唯一主题区）
        c.rect(7, 7, 8, 8, LIQ);
        c.set(7, 7, 0xFFFFFFFF);
        // 四角支撑脚
        foot(c, 1, 1);
        foot(c, 13, 1);
        foot(c, 1, 13);
        foot(c, 13, 13);
        return c;
    }

    /**
     * 单个 2×2 支撑脚：深色底 + 左上高光点
     *
     * @param c 目标画布
     * @param x 脚左上角横坐标
     * @param y 脚左上角纵坐标
     */
    static void foot(PixelCanvas c, int x, int y) {
        c.rect(x, y, x + 1, y + 1, FRAME_DARK);
        c.set(x, y, METAL_LIGHT);
    }

    /**
     * 展开图：3×2 网格（顶/正/背 上排，左/底/右 下排），深灰底 + 2px 间隔，
     * 每格 16×16 原生像素放大 8 倍（448×304），一次看全六面
     *
     * @throws IOException PNG 写出失败时抛出
     */
    static void unfolded() throws IOException {
        PixelCanvas big = new PixelCanvas(448, 304);
        big.fill(0xFF2A2E36);
        blit(big, top(), 2, 2, 8);
        blit(big, front(), 20, 2, 8);
        blit(big, back(), 38, 2, 8);
        blit(big, side(), 2, 20, 8);
        blit(big, bottom(), 20, 20, 8);
        blit(big, side(), 38, 20, 8);
        big.save("tools/texturegen/output/concept/chamber_unfolded_preview_8x.png");
    }

    /**
     * 把源画布按整数倍放大拷贝到目标画布指定位置（用于拼展开图）
     *
     * @param dst   目标画布
     * @param src   源画布（16×16）
     * @param ox    目标左上角横坐标
     * @param oy    目标左上角纵坐标
     * @param scale 放大倍数
     */
    static void blit(PixelCanvas dst, PixelCanvas src, int ox, int oy, int scale) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = src.get(x, y);
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        dst.set(ox + x * scale + dx, oy + y * scale + dy, argb);
                    }
                }
            }
        }
    }

    /**
     * 正式贴图模式：生成全部反照率贴图到 output/block/
     * <p>
     * 资产清单（12 张，全部 16×16）：
     * <ul>
     *   <li>base 5 张：enzyme_chamber_{front,side,back,top,bottom}.png（无 tint，
     *       主题槽位处保留基底材质，由贴片元素覆盖）</li>
     *   <li>side 镜像 1 张：enzyme_chamber_side_mirrored.png（西面用——西面
     *       UV u=16−z 会让同一张 side 贴图在东西两面互为镜像，为让管道在两
     *       面都位于前端，西面必须使用水平镜像贴图）</li>
     *   <li>theme 6 张 + lamp 1 张：灰度反照率（白=纯色、灰=暗化），内容画在
     *       贴片元素 UV 裁剪对应的坐标处，由 BlockColor 乘酶主题色得到
     *       "带内置左上光照的主题色"</li>
     * </ul>
     *
     * @throws IOException PNG 写出失败时抛出
     */
    static void textures() throws IOException {
        String out = "tools/texturegen/output/block";
        baseFront().save(out + "/enzyme_chamber_front.png");
        baseSide().save(out + "/enzyme_chamber_side.png");
        flipH(baseSide()).save(out + "/enzyme_chamber_side_mirrored.png");
        baseBack().save(out + "/enzyme_chamber_back.png");
        baseTop().save(out + "/enzyme_chamber_top.png");
        baseBottom().save(out + "/enzyme_chamber_bottom.png");
        themeWindow().save(out + "/enzyme_chamber_theme_window.png");
        themePipe().save(out + "/enzyme_chamber_theme_pipe.png");
        themePorthole().save(out + "/enzyme_chamber_theme_porthole.png");
        themeFlange().save(out + "/enzyme_chamber_theme_flange.png");
        themeTop().save(out + "/enzyme_chamber_theme_top.png");
        themeRing().save(out + "/enzyme_chamber_theme_ring.png");
        themeLamp().save(out + "/enzyme_chamber_theme_lamp.png");
        System.out.println("正式贴图生成完成: " + out);
    }

    /**
     * 正面基底：概念稿去掉全部主题区（凸字窗口液体→玻璃底；灯区为金属，
     * 由贴片覆盖——灯芯 2×2 无灯座黑框，避免旧版"黑空穴"观感）
     * <p>
     * 主题区保留基底材质的原因：贴片元素（tint 分区 quad）覆盖其上，
     * 基底只负责"贴片没盖到的边框/玻璃"，防止 UV 缝隙露出异常色
     *
     * @return 正面基底 16×16 画布
     */
    static PixelCanvas baseFront() {
        PixelCanvas c = front();
        c.rect(6, 4, 9, 6, GLASS);
        c.rect(3, 7, 12, 10, GLASS);
        return c;
    }

    /**
     * 侧面基底：概念稿去掉主题区（管道液体→管壁色、观察孔→玻璃底、灯→凹槽色）
     *
     * @return 侧面基底 16×16 画布
     */
    static PixelCanvas baseSide() {
        PixelCanvas c = side();
        c.vline(2, 13, 4, METAL_DARK);
        c.rect(10, 6, 12, 8, GLASS);
        c.set(10, 6, GLASS_HI);
        c.rect(11, 12, 12, 13, FRAME_DARK);
        return c;
    }

    /**
     * 背面基底：概念稿去掉主题区（大法兰内环→内环槽色、灯→凹槽色）
     *
     * @return 背面基底 16×16 画布
     */
    static PixelCanvas baseBack() {
        PixelCanvas c = back();
        c.rect(6, 7, 9, 8, FRAME_DARK);
        c.rect(11, 12, 12, 13, FRAME_DARK);
        return c;
    }

    /**
     * 顶面基底：概念稿去掉主题区（中央观察孔液体→玻璃底）
     *
     * @return 顶面基底 16×16 画布
     */
    static PixelCanvas baseTop() {
        PixelCanvas c = top();
        c.rect(6, 6, 9, 9, GLASS);
        return c;
    }

    /**
     * 底面基底：概念稿去掉主题区（中央接口环→金属）
     *
     * @return 底面基底 16×16 画布
     */
    static PixelCanvas baseBottom() {
        PixelCanvas c = bottom();
        c.rect(7, 7, 8, 8, METAL);
        return c;
    }

    /**
     * 水平镜像画布（16×16），用于西面侧贴图
     *
     * @param src 源画布
     * @return 镜像后的新画布
     */
    static PixelCanvas flipH(PixelCanvas src) {
        PixelCanvas dst = new PixelCanvas(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                dst.set(15 - x, y, src.get(x, y));
            }
        }
        return dst;
    }

    /**
     * 正面凸字形观察窗液体反照率：上横条 (6,4)-(9,6) + 下主体 (3,7)-(12,10)，
     * 各按顶行受光底行背光渐变
     * <p>
     * 内容坐标 = 贴片元素在正面（north，u=x、v=y）的 UV 裁剪区（两个
     * 贴片元素对应两段窗口），采样 1:1 无拉伸
     *
     * @return theme_window 16×16 画布
     */
    static PixelCanvas themeWindow() {
        PixelCanvas c = new PixelCanvas(16, 16);
        windowLiquidContent(c, 6, 4, 9, 6);
        windowLiquidContent(c, 3, 7, 12, 10);
        return c;
    }

    /**
     * 管道液体反照率：1px 宽竖柱画两处——x4（东面 UV [4,2,5,13]）与
     * x11（西面 UV [11,2,12,13]，西面 u=16−z 的镜像采样区），
     * 上亮下暗渐变与侧面管道一致
     *
     * @return theme_pipe 16×16 画布
     */
    static PixelCanvas themePipe() {
        PixelCanvas c = new PixelCanvas(16, 16);
        for (int y = 2; y <= 13; y++) {
            int col = y <= 6 ? LIQ_HI : (y <= 10 ? LIQ : LIQ_DARK);
            c.set(4, y, col);
            c.set(11, y, col);
        }
        return c;
    }

    /**
     * 观察孔液体反照率：3×3 内容画两处——(10,6)（东面）与 (3,6)（西面镜像区）
     *
     * @return theme_porthole 16×16 画布
     */
    static PixelCanvas themePorthole() {
        PixelCanvas c = new PixelCanvas(16, 16);
        portholeContent(c, 10, 6);
        portholeContent(c, 3, 6);
        return c;
    }

    /**
     * 单个 3×3 观察孔液体内容：基色 + 左上受光点
     *
     * @param c 目标画布
     * @param x 内容左上角横坐标
     * @param y 内容左上角纵坐标
     */
    static void portholeContent(PixelCanvas c, int x, int y) {
        c.rect(x, y, x + 2, y + 2, LIQ);
        c.set(x, y, 0xFFFFFFFF);
    }

    /**
     * 大法兰内环液体反照率（内容在 (6,7)-(9,8)，背面 south 面 UV [6,7,10,9]）
     *
     * @return theme_flange 16×16 画布
     */
    static PixelCanvas themeFlange() {
        PixelCanvas c = new PixelCanvas(16, 16);
        c.rect(6, 7, 9, 8, LIQ);
        c.set(6, 7, 0xFFFFFFFF);
        return c;
    }

    /**
     * 顶面观察孔液体反照率（内容在 (6,6)-(9,9)，顶面受光强整体偏亮）
     *
     * @return theme_top 16×16 画布
     */
    static PixelCanvas themeTop() {
        PixelCanvas c = new PixelCanvas(16, 16);
        c.hline(6, 9, 6, 0xFFFFFFFF);
        c.hline(6, 9, 7, LIQ_HI);
        c.hline(6, 9, 8, LIQ);
        c.hline(6, 9, 9, LIQ_DARK);
        return c;
    }

    /**
     * 中央接口环液体反照率（内容在 (7,7)-(8,8)，底面 down 面 UV [7,7,9,9]）
     *
     * @return theme_ring 16×16 画布
     */
    static PixelCanvas themeRing() {
        PixelCanvas c = new PixelCanvas(16, 16);
        c.rect(7, 7, 8, 8, LIQ);
        c.set(7, 7, 0xFFFFFFFF);
        return c;
    }

    /**
     * 指示灯反照率：正面两个 2×2 状态灯 at (3,4)/(11,4)（用户指定位置，
     * UV 与贴片元素一致）、东面 2×2 at (11,12)、西面 2×2 at (3,12)
     * （西面 u=16−z 的镜像采样区）
     *
     * @return theme_lamp 16×16 画布
     */
    static PixelCanvas themeLamp() {
        PixelCanvas c = new PixelCanvas(16, 16);
        lampContent(c, 3, 4);
        lampContent(c, 11, 4);
        lampContent(c, 11, 12);
        lampContent(c, 3, 12);
        return c;
    }

    /**
     * 单个 2×2 灯内容：基色 + 左上灯芯高光
     *
     * @param c 目标画布
     * @param x 内容左上角横坐标
     * @param y 内容左上角纵坐标
     */
    static void lampContent(PixelCanvas c, int x, int y) {
        c.rect(x, y, x + 1, y + 1, LAMP);
        c.set(x, y, 0xFFFFFFFF);
    }
}
