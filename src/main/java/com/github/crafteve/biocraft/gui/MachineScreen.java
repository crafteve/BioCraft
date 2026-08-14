package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineCategory;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.KineticConstants;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 酶工厂屏幕（experiment/gui-remake 分支全新重建）
 * <p>
 * 重建第二版（v2）：整张 gui_v1.png 作为 GUI 基底 1:1 blit；
 * 标题区（方块图标 + 缩写文本框 + displayname + INPUT/OUTPUT 标签）
 * 与滚动反应物卡片（JSON 条目数驱动 + 槽位元素）已就位，
 * 仪表盘与产物卡待逐项追加
 * <p>
 * GUI 画布尺寸与基底贴图一致（gui_v1.png 为 256×256）：
 * 画布左上角 = 贴图左上角，容器坐标（leftPos/topPos）即贴图 blit 原点
 * <p>
 * 标题区布局（GUI 内相对坐标）：
 * <ul>
 *   <li>方块物品图标：左上角 (8,8)，16×16（renderItem 标准物品图标尺寸）</li>
 *   <li>缩写文本框：1px 矩形框架（不倒圆角），左上角 (28,10)、下沿 y=21；
 *       宽 = 文字宽 + 左右各 2px 内边距 + 各 1px 边框；边框为主题色原色
 *       （补 alpha），填充为主题色向白混合 4/5；框内缩写与 displayname
 *       垂直居中于中轴线 15.5（8px 字形绝对定位 y=13）</li>
 *   <li>displayname：文本框右缘 + 4px，纯黑文字，vanilla 默认字体</li>
 *   <li>INPUT 标签：(9,30)；OUTPUT 标签：(195,30)，英文大写 8px 纯黑</li>
 *   <li>滚动卡片区（CardScrollArea 抽象，输入/输出各一实例）：
 *       输入区视口 (7,41)~(63,162)、输出区视口 (193,41)~(249,162)；
 *       卡片数 = JSON 物种条目数（输入 = 反应物、输出 = 产物）；
 *       每卡含槽位元素（slot.png 18×18 @卡片内 (1,2)，Slot 16×16 居中）
 *       与缩写（png 右侧 4px、与 png 上顶面平齐，物品色加深 1/5）、
 *       浓度进度条（槽位下方与卡片底端间居中，3px 高 54px 长）、
 *       浓度读数（槽位底面右侧 4px 向下 1px 为左下角，浅灰黑）；
 *       滚轮连续滚动 + 平滑插值，视口 scissor 裁剪</li>
 * </ul>
 * 字体约定：全程使用 Minecraft 自带字体（含中文的 unicode 自动回退），
 * 不加载任何自定义 TTF 字体资源
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    /** GUI 基底贴图（用户手绘，256×256，含背包区视觉） */
    private static final ResourceLocation GUI_BG =
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/gui_v1.png");

    /** GUI 画布宽 = 基底贴图宽（1:1 blit，杜绝缩放虚化） */
    private static final int GUI_W = 256;

    /** GUI 画布高 = 基底贴图高 */
    private static final int GUI_H = 256;

    /** 方块物品图标左上角（16×16 标准物品图标） */
    private static final int ITEM_X = 8, ITEM_Y = 8;

    /** 缩写文本框左上角 (28,10)，左下/右侧下沿 y=21（1px 矩形框架，不倒圆角） */
    private static final int ABBR_X = 28, ABBR_Y = 10;

    /** 文本框 y 范围 10~21（11px），中轴线 = (10+21)/2 = 15.5 */
    private static final int ABBR_Y_BOTTOM = 21;

    /** 缩写文本框内边距（文字左右各 2px） */
    private static final int ABBR_PAD = 2;

    /** 缩写文本框边框厚度（1px） */
    private static final int ABBR_BORDER = 1;

    /** displayname 与文本框右缘的间距（4px） */
    private static final int NAME_GAP = 4;

    /** displayname 文字颜色（纯黑） */
    private static final int NAME_COLOR = 0xFF000000;

    /** INPUT 标签左上角（英文大写，vanilla 8px 字体） */
    private static final int INPUT_X = 9, INPUT_Y = 30;

    /** OUTPUT 标签左上角 */
    private static final int OUTPUT_X = 195, OUTPUT_Y = 30;

    /** REACTION 标签左上角（y 与 INPUT 上顶面对齐） */
    private static final int REACTION_X = 71, REACTION_Y = 30;

    /** 反应方程式居中范围 x 67~188（中心 127.5） */
    private static final int EQ_X0 = 67, EQ_X1 = 188;

    /** 反应方程式顶部 y（REACTION 内容底部 37 + 4px），8px 不缩放 */
    private static final int EQ_Y = 41;

    /**
     * 反应方程式美化框：y 38~50（高 12px）——实测框距文字上顶面 1px
     * 下顶面 2px，上顶面补 1px 后上下均为 2px（框上边上移 1、高 +1）
     */
    private static final int EQ_BOX_Y = EQ_Y - 3, EQ_BOX_H = 12;

    /** 反应类型徽章 y（与 REACTION 上顶面对齐），x 右对齐方程式框右缘 */
    private static final int TAG_Y = REACTION_Y;

    // v-t 通量折线图（REACT 框下方）
    /** v-t 图顶部 y：REACT 框底（38+12=50）+ 4px */
    private static final int VT_Y = EQ_BOX_Y + EQ_BOX_H + 4;

    /** v-t 图高度 60px */
    private static final int VT_H = 60;

    /** v-t 图左边缘 x（Y 轴，工作区 67~188 左减 3px） */
    private static final int VT_X0 = 70;

    /** v-t 图右边缘 x（工作区右减 3px） */
    private static final int VT_X1 = 185;

    /** 格子宽（1s/格，12px；10 点 9 段 = 108px，点 70..178） */
    private static final int VT_GRID_W = 12;

    /** 窗口点数（1s 一点，共 10 点 = 0~9s） */
    private static final int VT_POINTS = 10;

    /** X 轴横线右端 = 箭头屁股 = 最右点消失处（点区右端） */
    private static final int VT_AXIS_END = VT_X0 + (VT_POINTS - 1) * VT_GRID_W;

    /** 采样间隔（1s = 20 tick） */
    private static final int VT_SAMPLE_TICKS = 20;

    /** 坐标轴颜色（深灰） */
    private static final int AXIS_COLOR = 0xFF555555;

    // 滚动卡片布局常量统一引用 MachineMenu（Menu 与 Screen 共享，全酶工厂写死）

    /** 槽位贴图（slot.png 18×18）资源 */
    private static final ResourceLocation SLOT =
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/slot.png");

    /** 卡片颜色（#c6c6c6 补 alpha） */
    private static final int CARD_COLOR = 0xFFC6C6C6;

    /** 浓度数据文字颜色（浅灰黑） */
    private static final int CONC_TEXT_COLOR = 0xFF777777;

    /** 进度条轨道颜色（浅灰，物品色为填充） */
    private static final int BAR_TRACK = 0xFFE0E0E0;

    /** 每个滚轮刻度移动的像素量（连续像素滚动，非逐张步进） */
    private static final double SCROLL_PIXELS_PER_NOTCH = 20.0;

    /** 滚动插值系数（每 tick 向目标偏移逼近的比例，越大越跟手） */
    private static final double SCROLL_LERP = 0.25;

    private final EnzymeFactoryBlockEntity blockEntity;
    private final EnzymeFactoryData enzymeData;

    /** 输入滚动卡片区（反应物，容器 x=7） */
    private final CardScrollArea inputArea;

    /** 输出滚动卡片区（产物，容器 x=193） */
    private final CardScrollArea outputArea;

    /** v-t 通量采样环形缓冲（每 0.5s 一点，窗口 VT_POINTS） */
    private final double[] vtFlux = new double[VT_POINTS];

    /** 环形缓冲写入下标 */
    private int vtIndex;

    /** 已采样点数（窗口未满时决定绘制起点） */
    private int vtSampleCount;

    /** 采样 tick 计数（每 VT_SAMPLE_TICKS 采样一点） */
    private int vtTickCounter;

    /**
     * @param menu            菜单实例
     * @param playerInventory 玩家物品栏
     * @param title           窗口标题
     */
    public MachineScreen(MachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        this.blockEntity = menu.getBlockEntity();
        this.enzymeData = menu.getEnzymeData();
        this.inputArea = new CardScrollArea(MachineMenu.SCROLL_X, 0, enzymeData.reactants());
        this.outputArea = new CardScrollArea(MachineMenu.OUTPUT_SCROLL_X,
                enzymeData.reactants().size(), enzymeData.products());
        initVtHistory(menu.getFluxHistory());
    }

    /**
     * 用服务端下发的历史初始化 v-t 环形缓冲（打开瞬间折线图即有数据）
     * <p>
     * 历史为每 tick 通量×1000（旧→新，200 tick = 10s）；从最新端往回
     * 按 1s（20 tick）采样，收集后反序写入环形缓冲——最新样本落在
     * vtIndex-1，与绘制循环"最新点在最左端"的取数逻辑一致；
     * 从最新端采样保证打开瞬间左端显示的是最近状态而非 10 秒前
     *
     * @param history 历史快照（旧→新，可能为空数组）
     */
    private void initVtHistory(int[] history) {
        if (history.length == 0) {
            return;
        }
        int max = Math.min((history.length + VT_SAMPLE_TICKS - 1) / VT_SAMPLE_TICKS, VT_POINTS);
        double[] samples = new double[max];
        int n = 0;
        for (int t = history.length - 1; t >= 0 && n < max; t -= VT_SAMPLE_TICKS) {
            samples[n++] = history[t] / 1000.0;
        }
        for (int i = n - 1; i >= 0; i--) {
            vtFlux[vtIndex] = samples[i];
            vtIndex = (vtIndex + 1) % VT_POINTS;
        }
        vtSampleCount = n;
    }

    /**
     * 渲染入口：super（背景 + 槽位 + 物品）+ 悬停物品 tooltip
     * <p>
     * 1.21.1 的 AbstractContainerScreen.render 不再渲染悬停槽位 tooltip，
     * 必须由子类显式调用 renderTooltip（见 AGENTS.md 欠账 13）
     *
     * @param graphics    渲染器
     * @param mouseX      鼠标 x（屏幕坐标）
     * @param mouseY      鼠标 y（屏幕坐标）
     * @param partialTick 部分 tick（渲染插值）
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 动态槽位 tooltip：物种槽 isActive=false 不进入 vanilla hoveredSlot
        // 机制，悬停时手动渲染物品 tooltip（与背包槽的 renderTooltip 互补）
        Slot hovered = findDynamicSlot(mouseX, mouseY);
        if (hovered != null && hovered.hasItem()) {
            graphics.renderTooltip(this.font, hovered.getItem(), mouseX, mouseY);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 主画布：基底贴图 1:1 blit + 标题区（方块图标 + 缩写文本框）
     * <p>
     * 注意 renderBg 阶段尚未平移 leftPos/topPos，所有坐标必须加容器偏移
     * （与 renderSlot 的相对坐标语义不同）
     *
     * @param graphics    渲染器
     * @param partialTick 部分 tick
     * @param mouseX      鼠标 x
     * @param mouseY      鼠标 y
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(GUI_BG, this.leftPos, this.topPos, 0, 0, GUI_W, GUI_H, GUI_W, GUI_H);
        drawTitleArea(graphics);
        drawReactionArea(graphics);
        inputArea.draw(graphics);
        outputArea.draw(graphics);
        drawVtChart(graphics);
    }

    /**
     * v-t 通量折线图：REACT 框下方 4px、高 60px、宽 = 工作区 − 6px
     * <p>
     * 布局（与用户给定约束一一对应）：
     * <ul>
     *   <li>Y 轴：x=70（图像左边缘），60×1px 深灰竖线 + 箭头——箭头顶端
     *       与线段结束点（顶端）重合：尖端行 = 轴顶行，向下张开 3 行</li>
     *   <li>X 轴（v=0 基准线）：横线 70..185 + 右端箭头（尖端朝右，
     *       尖端 = 线段右端，向左张开 3 列）；v=0 按值域比例定位——
     *       [-vmaxRShow, +vmaxFShow]，对称可逆居中、不可逆贴底</li>
     *   <li>y 轴满刻度用"饱和可达速率"：引擎通量在底物/产物满堆（浓度 1）
     *       时趋近 vmax·∏(1/Km)/(1+∏(1/Km)) 而非 vmax（Km 饱和项），
     *       以可达速率作满刻度后满堆工况恰好顶到 y 最大/最小值
     *       （相对位置 0 和 60）</li>
     *   <li>折线（从左往右滚动）：X 轴左侧 x=0（最新点）、右侧 x=10
     *       （最旧）；新点绘制在 x=0（左端），每 1s 采样后旧点右移一格，
     *       最右点被挤出窗口；2×2 主题色方形点（不加深）+ 1px 主题色
     *       折线连接</li>
     * </ul>
     *
     * @param graphics 渲染器
     */
    private void drawVtChart(GuiGraphics graphics) {
        int axX = this.leftPos + VT_X0;
        int topY = this.topPos + VT_Y;
        int bottomY = topY + VT_H;

        // Y 轴：60×1px 深灰竖线（图像左边缘）+ 箭头
        // 箭头尖端与线段结束点（轴顶）重合：尖端行 = topY，向下张开 3 行
        graphics.fill(axX, topY, axX + 1, bottomY + 1, AXIS_COLOR);
        graphics.fill(axX, topY, axX + 1, topY + 1, AXIS_COLOR);
        graphics.fill(axX - 1, topY + 1, axX + 2, topY + 4, AXIS_COLOR);

        // v 值域：[-vmaxR, vmaxF]（正向 kcat/TIME_SCALE；逆向 Haldane）
        ReactionDefinition definition = blockEntity.getSimulator().getDefinition();
        double vmaxF = definition.getVmaxF();
        double vmaxR = definition.isReversible()
                ? definition.vmaxBForTemperature(KineticConstants.T0) : 0.0;
        // 饱和可达速率作满刻度：满堆（浓度 1）时引擎通量顶到 y 边界。
        // 可逆用共享分母形式、不可逆用米氏积形式——两者满堆可达不同
        // （实测 HK 不可逆 + ATP Km≈1.0 饱和到仅 0.45·Vmax，误用共享分母
        // 公式会标定偏大近 2 倍，导致满速只显示在 y 轴一半高度）
        double vmaxFShow = saturationReachable(vmaxF, definition.getRateReactants(),
                definition.isReversible());
        double vmaxRShow = definition.isReversible()
                ? saturationReachable(vmaxR, definition.getRateProducts(), true) : 0.0;
        double span = Math.max(vmaxFShow + vmaxRShow, 1e-9);

        // X 轴（v=0 基准线）：底部向上按 vmaxRShow/span 比例定位（不可逆贴底）；
        // 横线 70..178（箭头屁股 = 最右点消失处），箭头尖端朝右延伸 3 列
        int zeroY = bottomY - (int) Math.round(vmaxRShow / span * VT_H);
        int tailX = this.leftPos + VT_AXIS_END;
        graphics.fill(axX, zeroY, tailX + 1, zeroY + 1, AXIS_COLOR);
        graphics.fill(tailX, zeroY - 1, tailX + 3, zeroY + 2, AXIS_COLOR);
        graphics.fill(tailX + 3, zeroY, tailX + 4, zeroY + 1, AXIS_COLOR);

        // 折线：从左往右滚动——最新点在左端（x=70），旧点逐格右移，
        // 最右点恰好落在 X 轴箭头屁股（点区右端 = 消失处）
        int count = Math.min(vtSampleCount, VT_POINTS);
        if (count < 2) {
            return;
        }
        int theme = MachineCategory.byId(enzymeData.category()).getThemeColor();
        int lineColor = theme | 0xFF000000;
        int pointColor = theme;
        int latest = (vtIndex - 1 + VT_POINTS) % VT_POINTS;
        int prevX = 0, prevY = 0;
        for (int i = 0; i < count; i++) {
            double v = vtFlux[(latest - i + VT_POINTS) % VT_POINTS];
            int x = axX + i * VT_GRID_W;
            int y = bottomY - (int) Math.round((v + vmaxRShow) / span * VT_H);
            y = Math.max(topY, Math.min(bottomY, y));
            if (i > 0) {
                drawLine(graphics, prevX, prevY, x, y, lineColor);
            }
            graphics.fill(x - 2, y - 2, x + 2, y + 2, pointColor);
            prevX = x;
            prevY = y;
        }
    }

    /**
     * 饱和可达速率：底物/产物满堆（浓度 1）时引擎通量能逼近的最大值
     * <p>
     * 两种速率形式满堆可达不同：
     * <ul>
     *   <li>可逆（共享分母）：v = vmax·∏(1/Km)/(1+∏(1/Km))，浓度 1 时
     *       米氏项 S/(Km+S) 换成比值 S/Km 后饱和较弱（如 PGI ≈ 0.7·vmax）</li>
     *   <li>不可逆（米氏积）：v = vmax·∏(1/(1+Km))，S/(Km+S) 在 S=1 时
     *       保留完整饱和（如 HK 的 ATP Km=1.12 → 0.47 倍，饱和强烈）——
     *       误用共享分母公式会标定偏大近 2 倍</li>
     * </ul>
     * 引擎饱和有界是正确物理，这里只做显示标定不做引擎修改
     *
     * @param vmax             方向最大速率（正向或逆向）
     * @param entries          该方向的速率项条目（含 Km 堆叠分数）
     * @param sharedDenominator true = 可逆共享分母形式，false = 不可逆米氏积形式
     * @return 饱和可达速率（>0）
     */
    private static double saturationReachable(double vmax,
                                              List<ReactionDefinition.SpeciesEntry> entries,
                                              boolean sharedDenominator) {
        double product = 1.0;
        for (ReactionDefinition.SpeciesEntry entry : entries) {
            if (entry.kmFraction() > 0) {
                if (sharedDenominator) {
                    product *= Math.pow(1.0 / entry.kmFraction(), entry.coeff());
                } else {
                    product *= Math.pow(1.0 / (1.0 + entry.kmFraction()), entry.coeff());
                }
            }
        }
        if (sharedDenominator) {
            return vmax * product / (1.0 + product);
        }
        return vmax * product;
    }

    /**
     * 画 1px 折线段（按 x/y 步进的整数插值，双轴均分步数防断线）
     *
     * @param graphics 渲染器
     * @param x1       起点 x（屏幕坐标）
     * @param y1       起点 y（屏幕坐标）
     * @param x2       终点 x（屏幕坐标）
     * @param y2       终点 y（屏幕坐标）
     * @param color    线段颜色
     */
    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    /**
     * 反应区：REACTION 标签 + 方程式美化框 + 反应方程式（8px 不缩放）
     * <p>
     * 方程式基于 JSON 解析分段绘制：
     * <ul>
     *   <li>系数（>1 时前缀）与物质缩写使用对应物品色（与卡片缩写同步
     *       加深 1/5）；"+" 与 ⇌/→ 符号为纯黑</li>
     *   <li>美化框：覆盖整个反应区宽（x 67~188）、高 11px（y 39~50），
     *       主题色 1px 边框 + 浅填充，与标题区缩写文本框同风格</li>
     *   <li>方程式 8px 原尺寸居中于框内（中心 x 127.5，y=41 顶部）——
     *       早期 2x 放大版过大显示不全，已改回不缩放</li>
     * </ul>
     *
     * @param graphics 渲染器
     */
    private void drawReactionArea(GuiGraphics graphics) {
        // REACTION 标签：英文大写，vanilla 8px 字体，纯黑，y 与 INPUT 上顶面对齐
        graphics.drawString(this.font, "REACTION",
                this.leftPos + REACTION_X, this.topPos + REACTION_Y, NAME_COLOR, false);

        int theme = MachineCategory.byId(enzymeData.category()).getThemeColor();

        // 反应类型徽章：REV（可逆）/ IRR（不可逆），主题色加深文字，
        // y 与 REACTION 上顶面对齐，x 右对齐方程式框右缘
        String revTag = enzymeData.reversible() ? "REV" : "IRR";
        graphics.drawString(this.font, revTag,
                this.leftPos + EQ_X1 - this.font.width(revTag),
                this.topPos + TAG_Y, darken(theme), false);

        // 分段构建方程式（段 = 系数/缩写/符号 + 各自颜色）
        List<EqSegment> segments = new ArrayList<>();
        appendEqSide(segments, enzymeData.reactants());
        // 可逆符号 MC 无字形（⇌ 回退难看），统一改用 "="（可逆）/ "→"（不可逆）
        segments.add(new EqSegment(enzymeData.reversible() ? "=" : "→", NAME_COLOR));
        appendEqSide(segments, enzymeData.products());

        // 总宽（8px）→ 居中于 67~188：起点 = 中心 − 半宽
        int totalW = 0;
        for (EqSegment segment : segments) {
            totalW += this.font.width(segment.text());
        }
        int x0 = (EQ_X0 + EQ_X1) / 2 - totalW / 2;

        // 美化框：宽 67~188 左右各回缩 1px（68~187，fill 半开区间）、
        // 高 12px（38~50），主题色 1px 边框 + 浅填充，与标题区文本框同风格
        int borderColor = theme | 0xFF000000;
        int fillColor = lighten(theme);
        int boxX0 = this.leftPos + EQ_X0 + 1;
        int boxX1 = this.leftPos + EQ_X1 - 1;
        int boxY0 = this.topPos + EQ_BOX_Y;
        int boxY1 = this.topPos + EQ_BOX_Y + EQ_BOX_H;
        graphics.fill(boxX0, boxY0, boxX1 + 1, boxY1 + 1, borderColor);
        graphics.fill(boxX0 + 1, boxY0 + 1, boxX1, boxY1, fillColor);

        // 方程式 8px 分段绘制（不缩放，逐段累加宽度）
        int cursor = 0;
        for (EqSegment segment : segments) {
            graphics.drawString(this.font, segment.text(),
                    this.leftPos + x0 + cursor, this.topPos + EQ_Y, segment.color(), false);
            cursor += this.font.width(segment.text());
        }
    }

    /**
     * 追加一侧物种段：系数（>1 时前缀）+ 缩写，同物品色，段间以 "+" 分隔
     *
     * @param segments 段列表（追加目标）
     * @param specs    反应物或产物条目列表（JSON 解析顺序）
     */
    private void appendEqSide(List<EqSegment> segments, List<EnzymeFactoryData.SpeciesSpec> specs) {
        boolean first = true;
        for (EnzymeFactoryData.SpeciesSpec spec : specs) {
            if (!first) {
                segments.add(new EqSegment("+", NAME_COLOR));
            }
            first = false;
            MoleculeItem item = ModItems.byId(spec.item()).get();
            // 物品色与卡片缩写同步加深 1/5
            int color = darkenOneFifth(item.getTintColor());
            if (spec.count() > 1) {
                segments.add(new EqSegment(String.valueOf(spec.count()), color));
            }
            segments.add(new EqSegment(item.getAbbreviation(), color));
        }
    }

    /**
     * 方程式段：文字 + 颜色（物质/系数段 = 物品色，符号段 = 黑色）
     *
     * @param text  段文字（系数、缩写或符号）
     * @param color 段颜色（ARGB）
     */
    private record EqSegment(String text, int color) {
    }

    /**
     * 滚轮事件：悬停在任一滚动卡片视口内时接管滚轮，按像素连续滚动
     * <p>
     * 悬停判定用屏幕坐标减去容器偏移还原为 GUI 相对坐标；输入区与
     * 输出区各自独立滚动（滚轮向上 verticalAmount>0 看更上方卡片）
     *
     * @param mouseX           鼠标 x（屏幕坐标）
     * @param mouseY           鼠标 y（屏幕坐标）
     * @param horizontalAmount 水平滚轮增量（本元素不使用）
     * @param verticalAmount   垂直滚轮增量（向上为正）
     * @return 是否消费事件
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        if (inputArea.contains(localX, localY)) {
            inputArea.scrollBy(verticalAmount);
            return true;
        }
        if (outputArea.contains(localX, localY)) {
            outputArea.scrollBy(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * 每 tick 平滑滚动插值（输入区与输出区各自独立）
     * <p>
     * 滚轮事件直接改目标值，本方法按 SCROLL_LERP 比例插值，
     * 差距小于 0.5px 时直接吸附（避免永不停歇的亚像素抖动）；
     * 槽位位置在绘制与命中的瞬间按当前偏移计算，天然与卡片同步
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        inputArea.tick();
        outputArea.tick();
        // v-t 采样：每 0.5s（10 tick）取当前净通量一点入环形缓冲
        vtTickCounter++;
        if (vtTickCounter >= VT_SAMPLE_TICKS) {
            vtTickCounter = 0;
            vtFlux[vtIndex] = menu.getFlux();
            vtIndex = (vtIndex + 1) % VT_POINTS;
            vtSampleCount = Math.min(vtSampleCount + 1, VT_POINTS);
        }
    }

    /**
     * 鼠标点击：优先命中滚动卡片内的动态槽位（手动计算位置）
     * <p>
     * 物种槽 isActive 恒 false 被 vanilla 完全跳过，此处复刻 vanilla
     * 点击核心逻辑：左键拾取/放置（PICKUP）、Shift+左键快速转移
     * （QUICK_MOVE）、右键拆分；双击快速收集暂不支持（vanilla 的
     * 双击状态字段为私有无法子类维护）
     *
     * @param mouseX 鼠标 x（屏幕坐标）
     * @param mouseY 鼠标 y（屏幕坐标）
     * @param button 鼠标按键（0 左键 / 1 右键）
     * @return 是否消费事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Slot slot = findDynamicSlot(mouseX, mouseY);
        if (slot != null && (button == 0 || button == 1)) {
            boolean shiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            net.minecraft.world.inventory.ClickType type = shiftDown
                    ? net.minecraft.world.inventory.ClickType.QUICK_MOVE
                    : net.minecraft.world.inventory.ClickType.PICKUP;
            this.slotClicked(slot, slot.index, button, type);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 按滚动偏移计算鼠标命中的动态槽位（输入区与输出区都查，无命中返回 null）
     * <p>
     * 槽位位置 = 卡片位置 + 卡片内相对 (2,3)，命中区域 16×16；
     * 与 CardScrollArea.draw 的绘制位置严格一致（同一公式）
     *
     * @param mouseX 鼠标 x（屏幕坐标）
     * @param mouseY 鼠标 y（屏幕坐标）
     * @return 命中的物种槽，未命中为 null
     */
    private Slot findDynamicSlot(double mouseX, double mouseY) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        Slot slot = inputArea.findSlot(localX, localY);
        if (slot != null) {
            return slot;
        }
        return outputArea.findSlot(localX, localY);
    }

    /**
     * 滚动卡片区域抽象：输入区与输出区共用一套布局/滚动/绘制/命中逻辑
     * <p>
     * 与 Menu 槽位的关系：本区域持有一段连续物种槽（baseSlot 起、
     * species 列表长度），槽位容器索引 = baseSlot + 卡片下标；
     * 输入区 = 反应物（槽 0 起），输出区 = 产物（槽 = 反应物数起）
     * <p>
     * 滚动机制（与用户给定的约束一一对应）：
     * <ul>
     *   <li>容器：左上角 (areaX, 41)，区域 y 41~162，宽 56</li>
     *   <li>卡片 56×28、间距 1，纵向按 29px 步进</li>
     *   <li>enableScissor 裁剪视口——超出视口上/下边界的卡片部分被裁掉，
     *       即"上方卡片消失、下方卡片出现"的滚动视觉</li>
     *   <li>按像素连续滚动（非逐张）：scrollBy 更新 scrollTarget，
     *       tick 中按 SCROLL_LERP 插值逼近；偏移钳制
     *       [0, 内容总高 − 视口高]，数据不足一屏时不滚动</li>
     * </ul>
     */
    private final class CardScrollArea {
        /** 滚动容器 x（GUI 相对，输入 7 / 输出 193） */
        private final int areaX;

        /** 本区域物种槽起点索引（输入 0 / 输出 = 反应物数） */
        private final int baseSlot;

        /** 本区域物种条目列表（反应物或产物，JSON 解析顺序） */
        private final List<EnzymeFactoryData.SpeciesSpec> species;

        /** 当前滚动像素偏移（渲染用，平滑插值后的显示值） */
        private double scrollOffset;

        /** 目标滚动像素偏移（滚轮事件直接更新，tick 中插值逼近） */
        private double scrollTarget;

        /**
         * @param areaX    滚动容器 x（GUI 相对）
         * @param baseSlot 本区域物种槽起点索引
         * @param species  本区域物种条目列表
         */
        CardScrollArea(int areaX, int baseSlot, List<EnzymeFactoryData.SpeciesSpec> species) {
            this.areaX = areaX;
            this.baseSlot = baseSlot;
            this.species = species;
        }

        /** 本区域卡片数（= 物种条目数） */
        int getCount() {
            return species.size();
        }

        /**
         * 悬停判定：鼠标 GUI 相对坐标是否在本区域视口内
         *
         * @param localX 鼠标 x（GUI 相对）
         * @param localY 鼠标 y（GUI 相对）
         * @return 是否在视口内
         */
        boolean contains(int localX, int localY) {
            return localX >= areaX && localX < areaX + MachineMenu.SCROLL_W
                    && localY >= MachineMenu.SCROLL_Y
                    && localY < MachineMenu.SCROLL_Y + MachineMenu.SCROLL_H;
        }

        /**
         * 按滚轮增量连续滚动（目标偏移钳制 [0, maxScroll]）
         *
         * @param verticalAmount 垂直滚轮增量（向上为正，向上看更上方卡片）
         */
        void scrollBy(double verticalAmount) {
            int maxScroll = Math.max(0, getCount() * MachineMenu.CARD_STEP
                    - MachineMenu.CARD_GAP - MachineMenu.SCROLL_H);
            this.scrollTarget = Math.max(0,
                    Math.min(scrollTarget - verticalAmount * SCROLL_PIXELS_PER_NOTCH, maxScroll));
        }

        /**
         * 每 tick 平滑插值：显示偏移向目标偏移逼近（差距 <0.5px 直接吸附）
         */
        void tick() {
            this.scrollOffset += (this.scrollTarget - this.scrollOffset) * SCROLL_LERP;
            if (Math.abs(this.scrollTarget - this.scrollOffset) < 0.5) {
                this.scrollOffset = this.scrollTarget;
            }
        }

        /**
         * 命中检测：鼠标 GUI 相对坐标命中的本区域槽位（无命中返回 null）
         * <p>
         * 槽位位置 = 卡片位置 + 卡片内相对 (2,3)，命中区域 16×16，
         * 与 draw 的绘制位置严格一致（同一公式）
         *
         * @param localX 鼠标 x（GUI 相对）
         * @param localY 鼠标 y（GUI 相对）
         * @return 命中的槽位，未命中为 null
         */
        Slot findSlot(int localX, int localY) {
            int offset = (int) Math.round(scrollOffset);
            for (int i = 0; i < getCount(); i++) {
                int sx = areaX + MachineMenu.SLOT_X;
                int sy = MachineMenu.SCROLL_Y + i * MachineMenu.CARD_STEP - offset + MachineMenu.SLOT_Y;
                if (localX >= sx && localX < sx + 16 && localY >= sy && localY < sy + 16) {
                    return menu.getSlot(baseSlot + i);
                }
            }
            return null;
        }

        /**
         * 绘制本区域全部卡片（视口 scissor 裁剪内）：
         * 卡片底色 + 槽位元素（slot.png 18×18 @卡片内 (1,2)，Slot 16×16 居中）
         * + 物品图标/数量 + hover 高亮 + 缩写（与槽位上顶面平齐、物品色加深
         * 1/5）+ 浓度进度条（槽位下方与卡片底端间居中，3px 高 54px 长）
         * + 浓度读数（槽位底面右侧 4px、向下 1px 为文字左下角，浅灰黑）
         *
         * @param graphics 渲染器
         */
        void draw(GuiGraphics graphics) {
            int x = leftPos + areaX;
            int y = topPos + MachineMenu.SCROLL_Y;
            graphics.enableScissor(x, y, x + MachineMenu.SCROLL_W, y + MachineMenu.SCROLL_H);
            int offset = (int) Math.round(scrollOffset);
            for (int i = 0; i < getCount(); i++) {
                int cardY = y + i * MachineMenu.CARD_STEP - offset;
                graphics.fill(x, cardY, x + MachineMenu.CARD_W, cardY + MachineMenu.CARD_H, CARD_COLOR);
                // 槽位元素：slot.png 18×18 @卡片内 (1,2)，Slot 16×16 居中于 (2,3)
                int pngX = x + MachineMenu.SLOT_PNG_X;
                int pngY = cardY + MachineMenu.SLOT_PNG_Y;
                Slot slot = menu.getSlot(baseSlot + i);
                graphics.blit(SLOT, pngX, pngY, 0, 0, 18, 18, 18, 18);
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, pngX + 1, pngY + 1,
                            (pngX + 1) + (pngY + 1) * imageWidth);
                    graphics.renderItemDecorations(font, stack, pngX + 1, pngY + 1, null);
                }
                // hover 高亮（半透明白，与 vanilla 同色，盖在物品上）
                // 1.21 Screen 无 mouseX/mouseY 字段，从 MouseHandler 取屏幕坐标
                int mx = (int) net.minecraft.client.Minecraft.getInstance().mouseHandler.xpos() - leftPos;
                int my = (int) net.minecraft.client.Minecraft.getInstance().mouseHandler.ypos() - topPos;
                if (mx >= pngX + 1 && mx < pngX + 17 && my >= pngY + 1 && my < pngY + 17) {
                    graphics.fill(pngX + 1, pngY + 1, pngX + 17, pngY + 17, 0x80FFFFFF);
                }

                // 物品数据：颜色取 substances.json 解析出的物品染色（24 位 RGB 补 alpha）
                String itemId = species.get(i).item();
                MoleculeItem item = ModItems.byId(itemId).get();
                // 缩写颜色 = 物品色加深 1/5（×4/5）
                int itemColor = darkenOneFifth(item.getTintColor());

                // 缩写：与槽位上顶面平齐（y = png 顶），颜色 = 物品色加深 1/5
                graphics.drawString(font, item.getAbbreviation(),
                        x + MachineMenu.SLOT_PNG_X + MachineMenu.NAME_DX,
                        pngY, itemColor, false);

                // 浓度：客户端重建引擎连续浓度 = (槽位数量 + 同步余量)/64，
                // 槽位数经菜单槽位同步、余量经 ContainerData 扩展通道同步
                // （客户端 BE 引擎浓度恒 0，直接读引擎会导致进度条/读数不显示）
                double concentration = Math.max(0.0, Math.min(
                        (stack.getCount() + menu.getRemainder(baseSlot + i)) / 64.0, 1.0));

                // 进度条：槽位下方与卡片底端之间（20..28）垂直居中，
                // 3px 高、54px 长（卡片宽 56 居中 → x+1），浅灰轨道 + 物品色填充
                int barY = cardY + MachineMenu.SLOT_PNG_Y + 18 + (8 - 3) / 2;
                graphics.fill(x + 1, barY, x + 1 + 54, barY + 3, BAR_TRACK);
                graphics.fill(x + 1, barY, x + 1 + (int) (54 * concentration), barY + 3, itemColor);

                // 浓度数据：槽位底面右侧 4px、向下偏移 1px 为文字左下角；
                // 浅灰黑文字，数值 = 浓度 × 堆叠数（连续值，允许小数）
                int numX = pngX + MachineMenu.NAME_DX;
                int numBottomY = pngY + 18 + 1;
                graphics.drawString(font,
                        "x" + String.format("%.2f", concentration * 64.0),
                        numX, numBottomY - 8, CONC_TEXT_COLOR, false);
            }
            graphics.disableScissor();
        }
    }

    /**
     * 标题区：方块物品图标 + 缩写文本框 + displayname + INPUT/OUTPUT 标签
     * <p>
     * 主题色取自酶类别（MachineCategory），加深/变浅由线性混合推导：
     * 边框色 = 主题色原色（补 alpha，不加深）；缩写文字色 = 主题色 × 3/5；
     * 填充色 = 主题色向白色混合 4/5（浅）
     * <p>
     * 文本框：1px 矩形框架（不倒圆角），左上 (28,10)、下沿 y=21，
     * 中轴线 15.5——框内文字与 displayname 均以 15.5 为垂直中轴：
     * 8px 字形中心 = y+3.5 → y = 12；16px 中文（MC 自动回退 unicode，
     * 超出框范围无视）中心 = y+8 → y = 8；均为绝对定位
     *
     * @param graphics 渲染器
     */
    private void drawTitleArea(GuiGraphics graphics) {
        // 方块 3D 物品图标：16×16 标准物品图标尺寸，左上角 (8,8)
        ItemStack blockStack = new ItemStack(blockEntity.getBlockState().getBlock());
        graphics.renderItem(blockStack, this.leftPos + ITEM_X, this.topPos + ITEM_Y);

        // 缩写文本框：1px 矩形框架（无圆角），y 范围 10~21
        String abbr = enzymeData.abbreviation();
        int theme = MachineCategory.byId(enzymeData.category()).getThemeColor();
        // 边框色必须补 alpha：MachineCategory 主题色是 24 位 RGB（alpha=0），
        // 直接 fill 会画出全透明矩形导致边框"直接消失"（实测 bug，已修复）
        int borderColor = theme | 0xFF000000;
        int textColor = darken(theme);
        int fillColor = lighten(theme);
        int textW = this.font.width(abbr);
        int boxW = textW + (ABBR_PAD + ABBR_BORDER) * 2;
        int boxX = this.leftPos + ABBR_X;
        int boxY = this.topPos + ABBR_Y;
        int boxY2 = this.topPos + ABBR_Y_BOTTOM + 1;
        graphics.fill(boxX, boxY, boxX + boxW, boxY2, borderColor);
        graphics.fill(boxX + ABBR_BORDER, boxY + ABBR_BORDER,
                boxX + boxW - ABBR_BORDER, boxY2 - ABBR_BORDER, fillColor);

        // 缩写文字：vanilla 默认 8px 位图字体，绝对定位（不写居中公式）：
        // 中轴线 15.5（y 范围 10~21），8px 字形中心 = y+3.5 → y = boxY + 2；
        // 实测文字整体向上偏移 1px，故下移 1px → y = boxY + 3
        // 左右于边框+内边距之后（x+3），文字色用加深主题色
        graphics.drawString(this.font, abbr,
                boxX + ABBR_BORDER + ABBR_PAD,
                boxY + 3, textColor, false);

        // displayname：文本框右缘 + 4px，纯黑文字，绝对定位：
        // 中文与英文统一按 8px 处理（实测 MC 中文渲染也是 8px 高，非 16px），
        // 与缩写文本同中轴且同步下移 1px（y = boxY + 3）
        String language = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        boolean chinese = language != null && language.startsWith("zh");
        String name = chinese ? enzymeData.nameZn() : enzymeData.nameEn();
        int nameX = boxX + boxW + NAME_GAP;
        int nameY = boxY + 3;
        graphics.drawString(this.font, name, nameX, nameY, NAME_COLOR, false);

        // INPUT / OUTPUT 标签：英文大写，vanilla 8px 字体，纯黑
        graphics.drawString(this.font, "INPUT", this.leftPos + INPUT_X, this.topPos + INPUT_Y, NAME_COLOR, false);
        graphics.drawString(this.font, "OUTPUT", this.leftPos + OUTPUT_X, this.topPos + OUTPUT_Y, NAME_COLOR, false);
    }

    /**
     * 颜色压暗（乘以 3/5 线性系数）
     *
     * @param color ARGB 颜色
     * @return 压暗后的 ARGB 颜色（alpha 保留）
     */
    private static int darken(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return 0xFF000000 | (r * 3 / 5 << 16) | (g * 3 / 5 << 8) | (b * 3 / 5);
    }

    /**
     * 颜色加深 1/5（乘以 4/5 线性系数，比 darken 的 3/5 更浅）
     *
     * @param color ARGB 颜色
     * @return 加深后的 ARGB 颜色（alpha 保留）
     */
    private static int darkenOneFifth(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return 0xFF000000 | (r * 4 / 5 << 16) | (g * 4 / 5 << 8) | (b * 4 / 5);
    }

    /**
     * 颜色提亮（向白色混合 4/5，比早期 3/5 更浅）
     *
     * @param color ARGB 颜色
     * @return 提亮后的 ARGB 颜色（alpha 保留）
     */
    private static int lighten(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return 0xFF000000 | ((r + (255 - r) * 4 / 5) << 16)
                | ((g + (255 - g) * 4 / 5) << 8) | (b + (255 - b) * 4 / 5);
    }

    /**
     * 不绘制 vanilla 标签（全部文字由 renderBg 自绘）
     *
     * @param graphics 渲染器
     * @param mouseX   鼠标 x
     * @param mouseY   鼠标 y
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 空实现
    }
}
