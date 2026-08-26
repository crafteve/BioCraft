package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.enzyme.EnzymeMachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.enzyme.EnzymeMachineBlockEntity.IoMode;
import com.github.crafteve.biocraft.compat.CompatRenderUtil;
import com.github.crafteve.biocraft.compat.EnzymeEquation;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.network.ServerboundSetIoModePacket;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.KineticConstants;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 酶工厂屏幕（experiment/gui-remake 分支全新重建，与 main 合并前定稿）
 * <p>
 * 整张 gui_v1.png（256×256）作为 GUI 基底 1:1 blit，容器坐标
 * （leftPos/topPos）即贴图 blit 原点；GUI 内相对坐标布局如下：
 * <ul>
 *   <li>标题区：方块物品图标 (8,8) 16×16；缩写文本框 (28,10) 下沿 21
 *       （1px 边框主题色 + 浅填充，中轴 15.5）；displayname 文本框右缘
 *       +4px；INPUT (9,30) / OUTPUT (195,30) 标签</li>
 *   <li>反应区：REACTION (71,30) + REV/IRR 徽章（右对齐 188）；方程式
 *       8px 分段彩色（物品色加深 1/5 / 黑色符号），超宽在 +/箭头附近
 *       换行，浅色主题底随行数增高</li>
 *   <li>滚动卡片区（CardScrollArea 抽象，输入/输出各一实例）：视口
 *       (7,41)~(63,153) 与 (193,41)~(249,153)（下边界上抬 9px，底部
 *       9px 为 IO 模式按钮区），卡片数 = JSON 物种条目数；
 *       每卡含槽位元素（slot.png 18×18 @卡片内 (1,2)，Slot 16×16 居中，
 *       可交互受限槽位）、缩写、浓度进度条与读数；滚轮连续滚动 +
 *       平滑插值，视口 scissor 裁剪。
 *       IO 模式按钮：滚动槽正下方 (7,153) 与 (193,153)，56×11px
 *       （下边界与中央反应速率区浅色背景底 164 齐平），
 *       主题色边框 + 浅色填充 + 模式文字（仅输入/仅输出/输入输出），
 *       点击循环切换本区域物品进出许可（GUI/管道/漏斗三路门控），
 *       悬停有原版槽位高亮同款浅色遮罩动画反馈。
 *       槽位原版化（修复原版/模组快捷键兼容）：Access Transformer 拆掉
 *       1.21.1 的 Slot.x/y final（见 META-INF/accesstransformer.cfg），
 *       CardScrollArea.tick 每帧按滚动偏移写入物种槽坐标（未用槽位移
 *       出屏外）——vanilla 的 findSlot 命中/点击/双击收集/拖拽分裂/
 *       数字键/JEI-EMI U-R/悬停 tooltip 全部原生生效；
 *       物品与高亮仍自绘（renderSlot 对物种槽空实现 + isHighlightable
 *       =false，见 renderSlot 覆写说明）</li>
 *   <li>v-t 图：4x 超采样抗锯齿，Y 轴按刻度宽度自动定位、X 轴按
 *       vmax 比例定位（可逆居中/不可逆贴底），0.25s 一点 20 点折线（5 tick 窗口平均），
 *       刻度标注（/tick）</li>
 *   <li>平衡区：渐变平衡条（两端加深主题色中间白）+ log10(Q/Keq)
 *       缩放滑块 + Keq/Q 读数（左右对齐滑槽）</li>
 *   <li>速率区：居中显示 v=xxx（/tick）8px 黑色</li>
 * </ul>
 * 字体约定：全程使用 Minecraft 自带字体（含中文 unicode 自动回退），
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
     * 下顶面 2px，上顶面补 1px 后上下均为 2px（框上边上移 1、高 +1）；
     * 多行时高度随行数增加（每行 +10px），顶部 38 保持不动
     */
    private static final int EQ_BOX_Y = EQ_Y - 3, EQ_BOX_H = 12;

    /** 方程式行距（8px 字 + 2px 行距 = 10px） */
    private static final int EQ_ROW_STEP = 10;

    /** 方程式单行最大宽（x 67~188 全宽），超宽时在 + / 箭头附近换行 */
    private static final int EQ_ROW_MAX_W = EQ_X1 - EQ_X0;

    /** 反应类型徽章 y（与 REACTION 上顶面对齐），x 右对齐方程式框右缘 */
    private static final int TAG_Y = REACTION_Y;

    // v-t 通量折线图（反应区下方）
    /**
     * v-t 图顶部 y（单行方程式基准）：方程区底 50 + 1（空隙行）+
     * 4（背景上扩 3px 后内容定位同步下移）——背景顶恰在方程区底下方 1px
     */
    private static final int VT_Y_BASE = EQ_BOX_Y + EQ_BOX_H + 5;

    /** v-t 图高度 60px */
    private static final int VT_H = 60;

    /** 图表区浅色底 x 范围（与方程区浅色底 68~187 列对齐，fill 半开补 1px） */
    private static final int VT_BG_X0 = 68, VT_BG_X1 = 187;

    /** 格子宽（0.25s/格，5px；20 点 19 段 = 95px，箭头尖端受限 ≤ 背景右缘−4px） */
    private static final int VT_GRID_W = 5;

    /** 刻度文字与 Y 轴左缘的间隙（显示 px） */
    private static final int VT_TICK_GAP = 2;

    /** 速率实时显示区底部 y（GUI 相对，固定画到此处） */
    private static final int RATE_AREA_BOTTOM = 164;

    /** 窗口点数（0.25s 一点，共 20 点 = 0~5s） */
    private static final int VT_POINTS = 20;

    /** 采样间隔（5 tick = 0.25s，窗口平均：每点 = 连续 5 tick 的均值） */
    private static final int VT_SAMPLE_TICKS = 5;

    /**
     * v-t 图超采样倍数：4x 分辨率绘制 + pose 缩放回 1x——fill 矩形边缘经
     * GPU 线性插值即抗锯齿（等效 4x 超采样下采样）。图表区无文字，
     * 位图字体不参与缩放，无糊字风险
     */
    private static final int VT_SUPERSAMPLE = 4;

    /** 折线点尺寸（超采样像素，10px = 显示 2.5px，原 4px 缩 60%） */
    private static final int VT_POINT_SIZE = 10;

    /** 折线宽（超采样像素，5px = 显示 1.25px，原 1px 加粗 25%） */
    private static final int VT_LINE_WIDTH = 5;

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

    /** 能量卡主题色（浅绿 #4CAF50，浅灰卡片底上可读） */
    private static final int ENERGY_COLOR = 0xFF4CAF50;

    /** 能量卡槽位贴图 tint（#4CAF50 的 RGB 分量 0~1，setColor 用） */
    private static final float ENERGY_TINT_R = 76 / 255.0f;
    private static final float ENERGY_TINT_G = 175 / 255.0f;
    private static final float ENERGY_TINT_B = 80 / 255.0f;

    /** 每个滚轮刻度移动的像素量（连续像素滚动，非逐张步进） */
    private static final double SCROLL_PIXELS_PER_NOTCH = 20.0;

    /** 滚动插值系数（每 tick 向目标偏移逼近的比例，越大越跟手） */
    private static final double SCROLL_LERP = 0.25;

    private final EnzymeMachineBlockEntity blockEntity;

    /**
     * 当前酶数据（动态：每 tick 从 menu.getEnzymeData() 刷新，
     * GUI 打开期间放酶/换酶实时重建卡片区；无酶为 null）
     */
    private EnzymeFactoryData enzymeData;

    /** 当前酶 id 快照（酶变化检测，与重建联动） */
    private String currentEnzymeId = "";

    /**
     * 客户端只读引擎定义（有酶时由 enzymeData 构建，重建卡片区时刷新）
     * <p>
     * 客户端 BE 的 simulator 恒为 null（引擎只在服务端步进，客户端无浓度状态），
     * 渲染层需要速率项条目/可达通量等引擎只读信息时必须用本缓存——
     * 直接调 blockEntity.getSimulator() 会 NPE（实测"打开 GUI 崩溃"根因）
     */
    private com.github.crafteve.biocraft.reaction.ReactionDefinition clientDefinition;

    /**
     * 引擎物种下标 → 菜单槽位映射（fe 能量物种为 -1）
     * <p>
     * fe 加入后引擎物种表与菜单槽位不再 1:1（fe 无槽位），
     * computeQ 等按引擎下标取浓度的地方必须经本映射换算
     */
    private int[] speciesToMenuSlot = new int[0];

    /** 输入滚动卡片区（反应物，容器 x=7；无酶时空卡片区） */
    private CardScrollArea inputArea;

    /** 输出滚动卡片区（产物，容器 x=193；无酶时空卡片区） */
    private CardScrollArea outputArea;

    /** v-t 通量采样环形缓冲（每 0.25s 一点，窗口 VT_POINTS；点 = 5 tick 窗口平均） */
    private final double[] vtFlux = new double[VT_POINTS];

    /** 环形缓冲写入下标 */
    private int vtIndex;

    /** 已采样点数（窗口未满时决定绘制起点） */
    private int vtSampleCount;

    /** 采样 tick 计数（每 VT_SAMPLE_TICKS 落一点） */
    private int vtTickCounter;

    /** 当前 5 tick 采样窗口内的 flux 累加（窗口平均，落点后清零） */
    private double vtWindowSum;

    /** 反应方程式行数（换行后动态，v-t 图定位依赖） */
    private int equationRowCount = 1;

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
        this.currentEnzymeId = enzymeData == null ? "" : enzymeData.id();
        this.inputArea = new CardScrollArea(MachineMenu.SCROLL_X, java.util.Collections.emptyList());
        this.outputArea = new CardScrollArea(MachineMenu.OUTPUT_SCROLL_X, java.util.Collections.emptyList());
        rebuildEnzymeAreas();
        // 立即同步一次滚动槽坐标：Screen 构造后首个 containerTick 之前渲染
        // 就会发生，槽位初始坐标是屏外 (-100,-100)——不立即写坐标则首帧
        // 滚动区图标画在屏外不可见，表现为"重进 GUI 闪一下才显示"
        inputArea.tick();
        outputArea.tick();
        initVtHistory(menu.getFluxHistory());
    }

    /**
     * 每 tick 刷新：检测酶数据变化（放酶/换酶/取空）→ 重建卡片区与映射
     * <p>
     * 服务端换酶会清空物种槽内容并重置引擎，客户端经 DATA_ENZYME
     * 感知变化后重建渲染数据；同种酶数量增减（[E] 缩放）不触发。
     * 放在 containerTick 内（Screen.tick 为 final 无法覆写）
     * <p>
     * 有酶 → "无酶"切换防护：客户端 0 槽仍持有酶物品时，DATA_ENZYME
     * 的"无酶"信号视为同步瞬态异常（0 槽由菜单槽位同步、打开期间权威，
     * 而数据段可能受容器数据广播时序影响）——忽略本次变化，避免卡片区
     * 被错误清空（实测：平衡时取走产物后反应物区整片消失、重进 GUI
     * 恢复的 bug 根因路径）；0 槽同步为空（换酶取空）才是真无酶，正常切换
     */
    private void refreshEnzymeIfChanged() {
        EnzymeFactoryData current = menu.getEnzymeData();
        String id = current == null ? "" : current.id();
        if (!id.equals(currentEnzymeId)) {
            if (current == null && enzymeData != null) {
                ItemStack enzymeStack = blockEntity.getContainer().getItem(
                        com.github.crafteve.biocraft.blockentity.enzyme.EnzymeMachineBlockEntity.ENZYME_SLOT);
                if (!enzymeStack.isEmpty()) {
                    return;
                }
            }
            currentEnzymeId = id;
            this.enzymeData = current;
            rebuildEnzymeAreas();
            // 换酶/无酶后重置图表采样：旧酶的历史折线不再有意义
            java.util.Arrays.fill(vtFlux, 0.0);
            vtSampleCount = 0;
        }
    }

    /**
     * 按当前酶数据重建卡片区与物种映射（构造/换酶/无酶共用）
     * <p>
     * 无酶 → 空卡片区 + 空映射（Screen 只画告示与 0 槽）；
     * 有酶 → 按酶数据构建输入/输出卡片（容器槽位从 SPECIES_SLOT_BASE 起）
     */
    private void rebuildEnzymeAreas() {
        if (enzymeData == null) {
            this.speciesToMenuSlot = new int[0];
            this.clientDefinition = null;
            this.inputArea = new CardScrollArea(MachineMenu.SCROLL_X, java.util.Collections.emptyList());
            this.outputArea = new CardScrollArea(MachineMenu.OUTPUT_SCROLL_X, java.util.Collections.emptyList());
            return;
        }
        // 客户端只读定义：由酶数据临时构建（纯数据无状态，仅用于显示层
        // 取速率项条目与可达通量；服务端引擎实例不受影响）
        this.clientDefinition = enzymeData.buildSimulator().getDefinition();
        this.speciesToMenuSlot = buildSpeciesToMenuSlot(enzymeData);
        int inputSlots = nonEnergyCount(enzymeData.reactants());
        int speciesBase = com.github.crafteve.biocraft.blockentity.enzyme.EnzymeMachineBlockEntity.SPECIES_SLOT_BASE;
        this.inputArea = new CardScrollArea(MachineMenu.SCROLL_X,
                buildCards(enzymeData.reactants(), speciesBase));
        this.outputArea = new CardScrollArea(MachineMenu.OUTPUT_SCROLL_X,
                buildCards(enzymeData.products(), speciesBase + inputSlots));
    }

    /**
     * 引擎物种下标 → 容器槽位映射（fe 能量物种为 -1）
     * <p>
     * 规则与 Menu 槽位注册一致：0 槽为酶槽，反应物先产物后、跳过 fe
     * 从 SPECIES_SLOT_BASE 起依次编号；引擎物种表顺序 = 反应物 + 产物（含 fe）
     *
     * @param data 酶数据档案
     * @return 映射表（长度 = 引擎物种数）
     */
    private static int[] buildSpeciesToMenuSlot(EnzymeFactoryData data) {
        int total = data.reactants().size() + data.products().size();
        int[] mapping = new int[total];
        int speciesIndex = 0;
        int slot = com.github.crafteve.biocraft.blockentity.enzyme.EnzymeMachineBlockEntity.SPECIES_SLOT_BASE;
        for (EnzymeFactoryData.SpeciesSpec spec : data.reactants()) {
            mapping[speciesIndex++] =
                    com.github.crafteve.biocraft.reaction.EnergyKinetics.isEnergySpecies(spec.item()) ? -1 : slot++;
        }
        for (EnzymeFactoryData.SpeciesSpec spec : data.products()) {
            mapping[speciesIndex++] =
                    com.github.crafteve.biocraft.reaction.EnergyKinetics.isEnergySpecies(spec.item()) ? -1 : slot++;
        }
        return mapping;
    }

    /**
     * 物种条目中的非 fe 数（容器槽位分配游标用，规则与 Menu 一致）
     *
     * @param specs 物种条目列表
     * @return 非 fe 条目数
     */
    private static int nonEnergyCount(List<EnzymeFactoryData.SpeciesSpec> specs) {
        int count = 0;
        for (EnzymeFactoryData.SpeciesSpec spec : specs) {
            if (!com.github.crafteve.biocraft.reaction.EnergyKinetics.isEnergySpecies(spec.item())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 构建滚动卡片描述列表（fe 物种 → 能量卡，其余 → 物种卡）
     * <p>
     * 卡片顺序 = JSON 条目顺序，能量卡与其他卡片同滚动区同顺序；
     * 容器槽位游标从 baseSlot 起连续分配（跳过 fe）
     *
     * @param specs   物种条目列表（反应物或产物）
     * @param baseSlot 容器槽位起点
     * @return 卡片描述列表
     */
    private static List<CardSpec> buildCards(List<EnzymeFactoryData.SpeciesSpec> specs, int baseSlot) {
        List<CardSpec> cards = new ArrayList<>();
        int containerSlot = baseSlot;
        for (EnzymeFactoryData.SpeciesSpec spec : specs) {
            if (com.github.crafteve.biocraft.reaction.EnergyKinetics.isEnergySpecies(spec.item())) {
                cards.add(new EnergyCard(spec.count()));
            } else {
                cards.add(new SpeciesCard(spec, containerSlot++));
            }
        }
        return cards;
    }

    /**
     * 用服务端下发的历史初始化 v-t 环形缓冲（打开瞬间折线图即有数据）
     * <p>
     * 历史 = 服务端 5 tick 窗口平均后的点（旧→新，20 点 = 5s），
     * 直接顺序铺入环形缓冲——最旧先入、最新最后落 vtIndex-1，
     * 与绘制循环"最新点在最左端"的取数逻辑一致；
     * 服务端已按窗口平均，客户端无需再采样（旧版在客户端按 20 tick
     * 抽点降采样的逻辑已废弃）
     *
     * @param history 历史快照（旧→新，5 tick 平均点，可能为空数组）
     */
    private void initVtHistory(int[] history) {
        if (history.length == 0) {
            return;
        }
        int max = Math.min(history.length, VT_POINTS);
        for (int i = 0; i < max; i++) {
            vtFlux[vtIndex] = history[i] / 1000.0;
            vtIndex = (vtIndex + 1) % VT_POINTS;
        }
        vtSampleCount = max;
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
        this.renderTooltip(graphics, mouseX, mouseY);
        drawIoButtonTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 鼠标点击：INPUT/OUTPUT 的 IO 模式按钮左键点击 → 循环切换并发包
     * <p>
     * 按钮位于滚动槽正下方（不属于任何 Slot，vanilla 命中不到），
     * 必须在这里手动命中；命中后消费事件（不穿透到下层）
     *
     * @param mouseX 鼠标 x（屏幕坐标）
     * @param mouseY 鼠标 y（屏幕坐标）
     * @param button 按键（0 = 左键）
     * @return 是否消费事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isHoveringIoButton(MachineMenu.SCROLL_X, mouseX, mouseY)) {
                cycleIoMode(0);
                return true;
            }
            if (isHoveringIoButton(MachineMenu.OUTPUT_SCROLL_X, mouseX, mouseY)) {
                cycleIoMode(1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 循环切换指定区域的 IO 模式并发包到服务端
     * <p>
     * 客户端本地不直接改模式（服务端权威），发送后按钮显示随
     * ContainerData 广播在下一 tick 回刷——点击瞬间有轻微延迟属正常
     *
     * @param area 区域编码（0 = INPUT、1 = OUTPUT）
     */
    private void cycleIoMode(int area) {
        IoMode current = area == 0 ? menu.getInputIoMode() : menu.getOutputIoMode();
        PacketDistributor.sendToServer(new ServerboundSetIoModePacket(
                blockEntity.getBlockPos(), area, current.next().id()));
    }

    /**
     * 绘制 INPUT/OUTPUT 两个 IO 模式按钮（滚动槽下边界上抬 9px 腾出的空间）
     * <p>
     * 视觉：主题色 1px 边框 + 浅色填充（与标题栏缩写框同款配色），
     * 中央绘制模式文字（仅输入/仅输出/输入输出，加深主题色）；
     * 悬停时叠加原版槽位高亮同款浅色遮罩（fillGradient 半透明白），
     * 透明度随 guiTicks 正弦脉动形成动画反馈
     *
     * @param graphics 渲染器
     * @param mouseX   鼠标 x（屏幕坐标，悬停判定）
     * @param mouseY   鼠标 y（屏幕坐标，悬停判定）
     */
    private void drawIoButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        // 无酶态由 drawNoEnzymeArea 提前返回，按钮只在有酶态绘制
        int theme = enzymeData.color();
        drawIoButton(graphics, MachineMenu.SCROLL_X, menu.getInputIoMode(), theme,
                isHoveringIoButton(MachineMenu.SCROLL_X, mouseX, mouseY));
        drawIoButton(graphics, MachineMenu.OUTPUT_SCROLL_X, menu.getOutputIoMode(), theme,
                isHoveringIoButton(MachineMenu.OUTPUT_SCROLL_X, mouseX, mouseY));
    }

    /**
     * 绘制单个 IO 模式按钮（滚动槽底部，宽 56px、高 11px =
     * 滚动槽下边界上抬的 9px + 向下延长 2px 与中央速率区背景底齐平）
     *
     * @param graphics 渲染器
     * @param areaX    本区域滚动容器 x（GUI 相对，输入 7 / 输出 193）
     * @param mode     当前模式（决定文字）
     * @param theme    主题色（ARGB）
     * @param hovered  是否悬停（叠加遮罩动画）
     */
    private void drawIoButton(GuiGraphics graphics, int areaX, IoMode mode, int theme, boolean hovered) {
        int x = this.leftPos + areaX;
        int y = this.topPos + MachineMenu.IO_BUTTON_Y;
        int w = MachineMenu.SCROLL_W;
        int h = MachineMenu.IO_BUTTON_H;
        // 1px 边框（主题色）+ 内部浅色填充（标题栏缩写框同款配色）
        graphics.fill(x, y, x + w, y + h, theme | 0xFF000000);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, lighten(theme));
        // 模式文字（加深主题色，水平垂直居中；8px 字在 9px 高内）
        String label = switch (mode) {
            case INPUT_ONLY -> "仅输入";
            case OUTPUT_ONLY -> "仅输出";
            case BOTH -> "输入输出";
        };
        int textW = this.font.width(label);
        graphics.drawString(this.font, label,
                x + (w - textW) / 2, y + (h - 8) / 2, darken(theme), false);
        // 悬停遮罩：原版槽位高亮同款浅色遮罩（0x80FFFFFF 半透明白）+ 透明度脉动动画
        if (hovered) {
            double pulse = 0.5 + 0.5 * Math.sin(Minecraft.getInstance().gui.getGuiTicks() * 0.2);
            int alpha = 0x40 + (int) Math.round(0x40 * pulse);
            graphics.fillGradient(RenderType.guiOverlay(), x, y, x + w, y + h,
                    (alpha << 24) | 0xFFFFFF, (alpha << 24) | 0xFFFFFF, 0);
        }
    }

    /**
     * 悬停判定：鼠标是否落在指定区域的 IO 模式按钮矩形内（屏幕坐标）
     *
     * @param areaX  本区域滚动容器 x（GUI 相对）
     * @param mouseX 鼠标 x（屏幕坐标）
     * @param mouseY 鼠标 y（屏幕坐标）
     * @return true 表示悬停在按钮上
     */
    private boolean isHoveringIoButton(int areaX, double mouseX, double mouseY) {
        int x = this.leftPos + areaX;
        int y = this.topPos + MachineMenu.IO_BUTTON_Y;
        return mouseX >= x && mouseX < x + MachineMenu.SCROLL_W
                && mouseY >= y && mouseY < y + MachineMenu.IO_BUTTON_H;
    }

    /**
     * 悬停按钮时的说明 tooltip：区域名 + 当前模式语义 + 切换提示
     *
     * @param graphics 渲染器
     * @param mouseX   鼠标 x（屏幕坐标）
     * @param mouseY   鼠标 y（屏幕坐标）
     */
    private void drawIoButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int area;
        if (isHoveringIoButton(MachineMenu.SCROLL_X, mouseX, mouseY)) {
            area = 0;
        } else if (isHoveringIoButton(MachineMenu.OUTPUT_SCROLL_X, mouseX, mouseY)) {
            area = 1;
        } else {
            return;
        }
        IoMode mode = area == 0 ? menu.getInputIoMode() : menu.getOutputIoMode();
        String modeDesc = switch (mode) {
            case INPUT_ONLY -> "仅允许物品输入（禁止抽出）";
            case OUTPUT_ONLY -> "仅允许物品输出（禁止放入）";
            case BOTH -> "允许输入与输出";
        };
        graphics.renderTooltip(this.font, List.of(
                Component.literal((area == 0 ? "INPUT" : "OUTPUT") + " IO 模式：" + modeDesc),
                Component.literal("点击切换：仅输入 → 仅输出 → 输入输出")),
                java.util.Optional.empty(), mouseX, mouseY);
    }

    /**
     * 槽位物品渲染：物种槽自绘（物品图标 + 拖拽分裂预览 + 自动缩小的
     * 堆叠数），包 scissor 裁剪滚动视口；其余槽位（0 槽/背包）交 vanilla
     * <p>
     * 自绘原因：槽位容量参数化后堆叠数可达 128（3 位数），vanilla 的
     * renderItemDecorations 固定 8px 字号会大面积遮挡分子贴图——
     * 自绘按位数自动缩小（3 位 0.75、4 位及以上 0.55），右下角锚点缩放；
     * quickCraft 拖拽分裂的"选取槽位"半透明预览与黄色超限数字由 AT
     * 开放的字段（isQuickCrafting/quickCraftSlots/quickCraftingType）
     * 复刻 vanilla renderSlot 的同款逻辑（精妙存储同款 AT 路线）
     *
     * @param graphics 渲染器
     * @param slot     槽位实例
     */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        int base = com.github.crafteve.biocraft.blockentity.enzyme.EnzymeMachineBlockEntity.SPECIES_SLOT_BASE;
        if (slot.index >= base && slot.index < base + speciesSlotCount()) {
            graphics.enableScissor(this.leftPos + MachineMenu.SCROLL_X,
                    this.topPos + MachineMenu.SCROLL_Y,
                    this.leftPos + MachineMenu.OUTPUT_SCROLL_X + MachineMenu.SCROLL_W,
                    this.topPos + MachineMenu.SCROLL_Y + MachineMenu.SCROLL_H);
            renderSpeciesSlot(graphics, slot);
            graphics.disableScissor();
            return;
        }
        super.renderSlot(graphics, slot);
    }

    /**
     * 自绘物种槽：物品图标 + 拖拽分裂预览 + 自动缩小堆叠数（vanilla
     * renderSlot 的 quickCraft 分支同款逻辑，字段经 AT 开放）
     *
     * @param graphics 渲染器
     * @param slot     槽位实例
     */
    /**
     * 自绘物种槽：物品图标 + 拖拽分裂预览 + 自动缩小堆叠数（vanilla
     * renderSlot 的 quickCraft 分支同款逻辑，字段经 AT 开放）
     * <p>
     * 渲染数据源为**槽位真实物品**（所见即所得：显示的就是槽位内容，
     * 与服务端同步的槽位快照一致）；交互（点击/取物/快捷键）走原版
     * 槽位机制（AT + 坐标同步）。自绘原因：槽位容量参数化后堆叠数
     * 可达 128（3 位数），vanilla 的 renderItemDecorations 固定 8px
     * 字号会大面积遮挡分子贴图——自绘按位数自动缩小字号
     *
     * @param graphics 渲染器
     * @param slot     槽位实例
     */
    private void renderSpeciesSlot(GuiGraphics graphics, Slot slot) {
        ItemStack stack = slot.getItem();
        ItemStack carried = this.menu.getCarried();
        boolean preview = false;
        ItemStack renderStack = stack;
        String countText = null;
        // quickCraft 拖拽分裂预览（复刻 vanilla renderSlot 分支）：
        // 拖拽经过的槽位显示"放置后结果"的半透明预览 + 黄色超限数字
        if (this.isQuickCrafting && this.quickCraftSlots.contains(slot) && !carried.isEmpty()) {
            if (this.quickCraftSlots.size() == 1) {
                // vanilla 同款：拖拽源槽（唯一选中槽）不画预览
                return;
            }
            if (AbstractContainerMenu.canItemQuickReplace(slot, carried, true) && this.menu.canDragTo(slot)) {
                preview = true;
                int maxStack = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
                int existing = stack.isEmpty() ? 0 : stack.getCount();
                int place = AbstractContainerMenu.getQuickCraftPlaceCount(
                        this.quickCraftSlots, this.quickCraftingType, carried) + existing;
                if (place > maxStack) {
                    place = maxStack;
                    countText = ChatFormatting.YELLOW.toString() + maxStack;
                }
                renderStack = carried.copyWithCount(place);
            } else {
                this.quickCraftSlots.remove(slot);
                this.recalculateQuickCraftRemaining();
            }
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        if (preview) {
            // 半透明白底（拖拽分裂的"选取槽位"效果，vanilla 同色）
            graphics.pose().translate(0.0F, 0.0F, 50.0F);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FFFFFF);
            graphics.pose().translate(0.0F, 0.0F, -50.0F);
        }
        if (!renderStack.isEmpty()) {
            graphics.renderItem(renderStack, slot.x, slot.y, slot.x + slot.y * imageWidth);
            renderStackCount(graphics, renderStack, slot.x, slot.y, countText);
        }
        graphics.pose().popPose();
    }

    /**
     * 堆叠数渲染：≤99 走 vanilla 原样（renderItemDecorations），
     * ≥100 或自定义文本（quickCraft 黄色超限数字）自动缩小字号
     * <p>
     * 大堆叠（3 位以上）用 vanilla 8px 字体会大面积遮挡分子贴图——
     * 以槽位右下角为锚点缩放绘制（3 位 0.75、4 位及以上 0.55），
     * 数字始终贴右下角不溢出；z 提升 200 层盖在物品（z=100）上
     *
     * @param graphics     渲染器
     * @param stack        渲染物品堆
     * @param x            槽位 x（GUI 相对）
     * @param y            槽位 y（GUI 相对）
     * @param textOverride 自定义堆叠数字（null = 用 count）
     */
    private void renderStackCount(GuiGraphics graphics, ItemStack stack, int x, int y, String textOverride) {
        int count = stack.getCount();
        String text = textOverride;
        if (text == null) {
            if (count <= 1) {
                return;
            }
            if (count <= 99) {
                graphics.renderItemDecorations(this.font, stack, x, y, null);
                return;
            }
            text = String.valueOf(count);
        }
        float scale = text.length() >= 4 ? 0.55f : 0.75f;
        int textW = this.font.width(text);
        graphics.pose().pushPose();
        // 锚点 = 槽位右下角，先缩放再平移（数字贴右下角向内收缩）
        graphics.pose().translate(x + 16, y + 16, 200.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-textW, -8.0F, 0.0F);
        int color = textOverride != null ? 0xFFFF00 : 0xFFFFFF;
        graphics.drawString(this.font, text, 0, 0, color, true);
        graphics.pose().popPose();
    }

    /**
     * 固定物种槽数（与 Menu 注册一致，判断槽位是否物种槽用）
     *
     * @return 最大非 fe 物种数
     */
    private static int speciesSlotCount() {
        return com.github.crafteve.biocraft.init.EnzymeFactoryRegistry.maxNonFeSpeciesCount();
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
        drawEnzymeSlot(graphics);
        if (enzymeData == null) {
            // 无酶态：基底 + 0 槽 + 标题区 [unknown] 占位 + 三栏标签照常绘制，
            // 卡片/图表/平衡/速率区留空（不画任何酶内容）
            drawNoEnzymeArea(graphics);
            return;
        }
        drawTitleArea(graphics);
        drawReactionArea(graphics);
        inputArea.draw(graphics);
        outputArea.draw(graphics);
        drawIoButtons(graphics, mouseX, mouseY);
        drawVtChart(graphics);
        drawBalanceArea(graphics);
        drawRateArea(graphics);
    }

    /**
     * 0 槽（酶槽）背景：slot.png 18×18 绘制在 Slot 位置左上方 (Slot-1)——
     * 背景 (8,7)、Slot (9,8) 中心对齐（实测微调：背景相对初始位右移 1px）；
     * 物品图标/数量/hover 高亮由 vanilla renderSlot 渲染（原版 Slot 模式，
     * JEI U/R 快捷键经 hoveredSlot 机制可用）
     */
    private void drawEnzymeSlot(GuiGraphics graphics) {
        graphics.blit(SLOT, this.leftPos + MachineMenu.ENZYME_SLOT_X - 1,
                this.topPos + MachineMenu.ENZYME_SLOT_Y - 1, 0, 0, 18, 18, 18, 18);
    }

    /**
     * 无酶态绘制：标题区 [unknown] 占位缩写框 + INPUT/OUTPUT/REACTION 标签
     * <p>
     * 提示逻辑（玩家定稿）：无酶时三栏标签照样绘制，原先绘制酶缩写的位置
     * 绘制 [unknown]（与酶工厂 title 缩写框同格式，灰色主题）；
     * 卡片/图表/平衡/速率区不绘制（无酶数据），也不画黑色告示块
     */
    private void drawNoEnzymeArea(GuiGraphics graphics) {
        // 标题区：缩写文本框显示 [unknown]（灰色主题，格式与酶工厂 title 一致）
        drawAbbreviationBox(graphics, "[unknown]", 0xFF9E9E9E);
        // INPUT / OUTPUT 标签（与有酶态同位置）
        graphics.drawString(this.font, "INPUT", this.leftPos + INPUT_X, this.topPos + INPUT_Y, NAME_COLOR, false);
        graphics.drawString(this.font, "OUTPUT", this.leftPos + OUTPUT_X, this.topPos + OUTPUT_Y, NAME_COLOR, false);
        // REACTION 标签（与有酶态同位置，无方程内容）
        graphics.drawString(this.font, "REACTION",
                this.leftPos + REACTION_X, this.topPos + REACTION_Y, NAME_COLOR, false);
    }

    /**
     * 平衡区：图表区下方 1px 间隔，浅色主题底色 + 渐变平衡条 + 滑块 + Keq/Q 读数
     * <p>
     * 布局：
     * <ul>
     *   <li>浅色底 x 68..187（与图表区/方程区列对齐），高 20px</li>
     *   <li>渐变条：高 10px、宽 = 区宽 − 24 居中；水平渐变——两端为加深
     *       主题色、中间为白色（4x 逐列线性插值）</li>
     *   <li>滑块：黑色 1px 宽 × 条高，位置 = 平衡位置——log10(Q/Keq) 缩放
     *       （±3 数量级满行程），Q=Keq 居中、偏离滑向两端，钳制条内</li>
     *   <li>读数：条下方 2px，Keq 左对齐滑槽左缘、Q 右对齐滑槽右缘，
     *       2x 放大文字（4x 超采样缩 1/4 → 显示 4px，与图注同法）</li>
     * </ul>
     *
     * @param graphics 渲染器
     */
    private void drawBalanceArea(GuiGraphics graphics) {
        int ss = VT_SUPERSAMPLE;
        // 与图表区同法：4x 超采样坐标系绘制 + 缩 1/4——渐变逐列更平滑、
        // 滑块边缘抗锯齿；文字经 drawScaledText 2x 放大（显示 4px，与图注一致）
        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos + VT_BG_X0, this.topPos + balanceY(), 0);
        graphics.pose().scale(1.0F / ss, 1.0F / ss, 1.0F);

        int theme = enzymeData.color();
        int areaH = 20;
        int bgRight = (VT_BG_X1 - VT_BG_X0 + 1) * ss;

        // 浅色主题底（与图表区同色系、同列对齐）
        graphics.fill(0, 0, bgRight, areaH * ss, lighten(theme));

        // 渐变条：高 10px，宽 = 区宽 − 24 居中；4x 逐列插值（更平滑）
        int barW = (VT_BG_X1 - VT_BG_X0) - 24;
        int barX0 = 12 * ss;
        int barY = 2 * ss;
        int barH = 10 * ss;
        int deep = darken(theme);
        for (int col = 0; col < barW * ss; col++) {
            double t = Math.abs(col - (barW * ss - 1) / 2.0) / ((barW * ss - 1) / 2.0);
            graphics.fill(barX0 + col, barY, barX0 + col + 1, barY + barH,
                    lerpColor(deep, 0xFFFFFFFF, (float) (1.0 - t)));
        }

        // 滑块：黑色 1px 宽（4x）× 条高，log10(Q/Keq) 缩放定位，钳制在条内
        //（logRatio 钳制下限 1e-6 防 -inf，滑块位置天然不越界）
        double keq = enzymeData.keq();
        double q = computeQ();
        double logRatio = Math.log10(Math.max(q / keq, 1e-6));
        int mid = barX0 + barW * ss / 2;
        // ±3 个数量级对应条半宽（log 缩放）
        double halfRange = barW * ss / 2.0 / 3.0;
        int sliderX = mid + (int) Math.round(logRatio * halfRange);
        sliderX = Math.max(barX0, Math.min(barX0 + barW * ss - ss, sliderX));
        graphics.fill(sliderX, barY, sliderX + ss, barY + barH, 0xFF000000);

        // 读数：Keq 左对齐滑槽左缘、Q 右对齐滑槽右缘（均在条下方 2px），
        // 2x 放大绘制（4x 超采样缩 1/4 → 显示 4px，与图表区图注同一做法）
        String keqText = "Keq=" + formatTickValue(keq);
        String qText = "Q=" + formatTickValue(q);
        int textY = barY + barH + 2 * ss;
        drawScaledText(graphics, keqText, barX0, textY, 2.0F);
        drawScaledText(graphics, qText,
                barX0 + barW * ss - this.font.width(qText) * 2, textY, 2.0F);

        graphics.pose().popPose();
    }

    /**
     * 平衡区顶部 y：图表区背景下扩 3px 的底（vtY+63）+ 1px 间隔
     *
     * @return 平衡区顶部 GUI 相对 y
     */
    private int balanceY() {
        return vtY() + VT_H + 3 + 1;
    }

    /**
     * 速率实时显示区：平衡区下方 1px 间隔，浅色底画到 y=164，
     * 居中显示 v=xxx（/tick，8px 黑色字体，不超采样）
     * <p>
     * 数值 = 反应速率（反应次数/tick，主产物槽位投影增量/系数，
     * BE 每 tick 统计，1 tick 实时——引擎瞬时净通量在平衡区/抽取
     * 工况无法反映实际吞吐，已弃用）；2 位有效数字，
     * |v| < 0.05 显示 "0.0"（接近 0 判定）；
     * 区高不足 8px（多行方程式挤压）时跳过文字避免重叠
     *
     * @param graphics 渲染器
     */
    private void drawRateArea(GuiGraphics graphics) {
        int y0 = this.topPos + rateY();
        int y1 = this.topPos + RATE_AREA_BOTTOM;
        int theme = enzymeData.color();
        graphics.fill(this.leftPos + VT_BG_X0, y0,
                this.leftPos + VT_BG_X1 + 1, y1, lighten(theme));
        int h = y1 - y0;
        if (h < 8) {
            return;
        }
        double v = menu.getFlux();
        String vs = formatTickValue(v);
        String text = "v=" + vs + " /tick";
        int textW = this.font.width(text);
        int x = this.leftPos + (VT_BG_X0 + VT_BG_X1) / 2 - textW / 2;
        int y = y0 + (h - 8) / 2;
        graphics.drawString(this.font, text, x, y, 0xFF000000, false);
    }

    /**
     * 速率区顶部 y：平衡区（高 20）底 + 1px 间隔
     *
     * @return 速率区顶部 GUI 相对 y
     */
    private int rateY() {
        return balanceY() + 20 + 1;
    }

    /**
     * 计算当前浓度商 Q = ∏产物^系数 / ∏底物^系数
     * <p>
     * 浓度取客户端重建值（槽位数量 + 同步余量）/64，与进度条同源；
     * 速率项条目从引擎定义取（固定活性物种不参与）
     *
     * @return 浓度商 Q（>0）
     */
    private double computeQ() {
        ReactionDefinition definition = clientDefinition;
        double numerator = 1.0;
        for (ReactionDefinition.SpeciesEntry entry : definition.getRateProducts()) {
            numerator *= Math.pow(Math.max(concentrationOf(entry.index()), 1e-9), entry.coeff());
        }
        double denominator = 1.0;
        for (ReactionDefinition.SpeciesEntry entry : definition.getRateReactants()) {
            denominator *= Math.pow(Math.max(concentrationOf(entry.index()), 1e-9), entry.coeff());
        }
        return numerator / denominator;
    }

    /**
     * 物种浓度（客户端重建：槽位数量 + 同步余量）/64，
     * 钳制 [0, MAX_CONCENTRATION]（与引擎/进度条同源上限）
     * <p>
     * 入参为引擎物种下标（computeQ 的速率项条目下标），经
     * speciesToMenuSlot 映射到菜单槽位；fe 能量物种返回 0
     * （fe 不在速率项中，防御性兜底）
     *
     * @param speciesIndex 引擎物种下标
     * @return 浓度 0~MAX_CONCENTRATION
     */
    private double concentrationOf(int speciesIndex) {
        int slot = speciesIndex < speciesToMenuSlot.length ? speciesToMenuSlot[speciesIndex] : -1;
        if (slot < 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(
                (menu.getSlot(slot).getItem().getCount() + menu.getRemainder(slot)) / 64.0,
                com.github.crafteve.biocraft.reaction.KineticConstants.MAX_CONCENTRATION));
    }

    /**
     * 两色线性插值
     *
     * @param c1 起点颜色（ARGB）
     * @param c2 终点颜色（ARGB）
     * @param t  插值系数（0 = 全 c1，1 = 全 c2）
     * @return 插值后的颜色（alpha 恒 0xFF）
     */
    private static int lerpColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * v-t 通量折线图：方程区下方 1px 空隙、高 60px、浅色底 68~187（列对齐）
     * <p>
     * 高清渲染：整图在 4x 超采样坐标系内绘制（全部坐标 ×4），再经
     * pose.scale(1/4) 缩放回原尺寸——fill 矩形边缘线性插值 = 抗锯齿
     * <p>
     * 布局要点：
     * <ul>
     *   <li>浅色底上下各扩展 3px（刻度/箭头不越界）</li>
     *   <li>Y 轴（3px 宽）按刻度文字最大宽度自动右移——最长刻度右对齐
     *       轴左缘后左缘恰落背景左缘；箭头为 4 段渐变三角形</li>
     *   <li>X 轴（v=0 基准线，3px 高）：按 vmaxRShow/span 比例定位，
     *       可逆居中、不可逆贴底；右端箭头屁股 = 最右点消失处</li>
     *   <li>y 轴满刻度 = 饱和可达速率（可逆共享分母 / 不可逆米氏积），
     *       满堆工况恰好顶到 y 最大/最小值</li>
     *   <li>折线：0.25s 一点共 20 点（5 tick 窗口平均），从左往右滚动（最新在左端）；
     *       2.5px 主题色方形点 + 1.25px 主题色折线（中心对齐穿点）</li>
     *   <li>刻度：每 10px 一条，值 = v×3.2（/tick），2x 文字（显示 4px）</li>
     * </ul>
     *
     * @param graphics 渲染器
     */
    private void drawVtChart(GuiGraphics graphics) {
        int ss = VT_SUPERSAMPLE;
        // 4x 超采样：平移到图表区左上角（背景左缘 68），以 4x 分辨率局部坐标绘制，
        // 再 scale(1/4) 缩小回 1x——fill 边缘经线性插值即抗锯齿
        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos + VT_BG_X0, this.topPos + vtY(), 0);
        graphics.pose().scale(1.0F / ss, 1.0F / ss, 1.0F);

        int bottomY = VT_H * ss;
        int theme = enzymeData.color();

        // v 值域：[-vmaxR, vmaxF]（正向 kcat/TIME_SCALE；逆向 Haldane）
        ReactionDefinition definition = clientDefinition;
        // 满刻度用引擎可达通量（[E]=1 口径）：速率方程代入满堆浓度算出的
        // 最大通量，即"游戏内可达上限"——引擎直接给出，显示层只做 ×[E]
        // 线性倍率（Vmax = kcat × [E]，活性通道），随酶堆叠数动态缩放；
        // [E] 从 0 槽物品堆叠数取（经槽位广播同步，客户端可用）
        double enzymeCount = Math.max(1.0,
                menu.getSlot(EnzymeMachineBlockEntity.ENZYME_SLOT).getItem().getCount());
        double vmaxFShow = definition.forwardReachableFlux() * enzymeCount;
        double vmaxRShow = definition.reverseReachableFlux() * enzymeCount;
        double span = Math.max(vmaxFShow + vmaxRShow, 1e-9);

        // Y 轴 x 按刻度文字最大宽度自动缩放（右移）：
        // 最长刻度右对齐 Y 轴左缘，其左缘恰好落在背景左缘 68——
        // axisX 超采样 = maxTextW×2（2x 绘制） + 间隙×4
        int maxTickW = 0;
        for (int step = 0; step <= VT_H / 10; step++) {
            int p = step * 10;
            double v = vmaxFShow - span * p / VT_H;
            int w = this.font.width(formatAxisLabel(v * 3.2));
            maxTickW = Math.max(maxTickW, w);
        }
        int axisX = maxTickW * 2 + VT_TICK_GAP * ss;
        // 点区 9 段 + 箭头 3px：尖端不得超过背景右缘 − 4px（往回缩 4px）
        int tailX = axisX + (VT_POINTS - 1) * VT_GRID_W * ss;
        int bgRight = (VT_BG_X1 - VT_BG_X0 + 1) * ss;

        // 图表区浅色主题色背景：上下各扩展 3px（显示）——顶部/底部刻度
        // 标注与箭头超出原 0..60 边界，扩展后全部落在底色范围内
        graphics.fill(0, -ss * 3, bgRight, bottomY + ss * 3, lighten(theme));

        // 辅助网格线：刻度位置 1 超采样像素白色横线（缩放后 0.25px），
        // 便于对照刻度值识别通量位置
        for (int step = 0; step <= VT_H / 10; step++) {
            int gy = step * 10 * ss;
            graphics.fill(axisX, gy, bgRight, gy + 1, 0xFFFFFFFF);
        }

        // 折线：绘制在轴与刻度之下（先画，后画的轴/刻度/单位覆盖其上，
        // 避免折线遮挡单位等标记——原顺序折线最后绘制盖住"/tick"）
        // 从左往右滚动——最新点在左端（Y 轴处），旧点逐格右移，
        // 最右点恰好落在 X 轴箭头屁股（消失处）
        // 数据点 = 主产物增量（物品/tick，BE 投影统计）；y 轴基准（span/
        // vmaxRShow）是引擎可达通量（浓度/秒）——绘制时换算同口径：
        // 物品/tick / 3.2 = 浓度/秒（1 tick = 64 个 × 0.05s）
        int count = Math.min(vtSampleCount, VT_POINTS);
        if (count >= 2) {
            int lineColor = theme | 0xFF000000;
            // 点色必须补 alpha：数据表主题色为 ARGB 已含 alpha，
            // 补位是保险（fill 全透明会画不出点，实测 bug 曾出现）
            int pointColor = theme | 0xFF000000;
            int latest = (vtIndex - 1 + VT_POINTS) % VT_POINTS;
            int prevX = 0, prevY = 0;
            for (int i = 0; i < count; i++) {
                double v = vtFlux[(latest - i + VT_POINTS) % VT_POINTS] / 3.2;
                int x = axisX + i * VT_GRID_W * ss;
                int y = bottomY - (int) Math.round((v + vmaxRShow) / span * VT_H * ss);
                y = Math.max(0, Math.min(bottomY, y));
                if (i > 0) {
                    drawLine(graphics, prevX, prevY, x, y, VT_LINE_WIDTH, lineColor);
                }
                graphics.fill(x - VT_POINT_SIZE / 2, y - VT_POINT_SIZE / 2,
                        x + VT_POINT_SIZE / 2, y + VT_POINT_SIZE / 2, pointColor);
                prevX = x;
                prevY = y;
            }
        }

        // Y 轴：3px 宽（原 4px 减 20%）；箭头尖端（轴顶）向下渐变三角形
        //（3→8→12→16px 宽，超采样下更精细）
        graphics.fill(axisX, 0, axisX + ss - 1, bottomY + ss - 1, AXIS_COLOR);
        graphics.fill(axisX, 0, axisX + ss - 1, ss - 1, AXIS_COLOR);
        graphics.fill(axisX - ss / 2, ss - 1, axisX + ss + ss / 2, ss * 2 - 1, AXIS_COLOR);
        graphics.fill(axisX - ss, ss * 2 - 1, axisX + ss * 2, ss * 3 - 1, AXIS_COLOR);
        graphics.fill(axisX - ss + ss / 2, ss * 3 - 1, axisX + ss * 2 + ss / 2, ss * 4 - 1, AXIS_COLOR);

        // X 轴（v=0 基准线）：3px 高；底部向上按 vmaxRShow/span 比例定位；
        // 箭头尖端朝右 4 段渐变（屁股 16px 高 → 尖端 4px 高，段宽 3px）
        int zeroY = bottomY - (int) Math.round(vmaxRShow / span * VT_H * ss);
        graphics.fill(axisX, zeroY, tailX + ss - 1, zeroY + ss - 1, AXIS_COLOR);
        graphics.fill(tailX, zeroY - ss * 2, tailX + ss - 1, zeroY + ss * 2, AXIS_COLOR);
        graphics.fill(tailX + ss - 1, zeroY - ss * 2 + 2, tailX + ss * 2 - 1, zeroY + ss * 2 - 2, AXIS_COLOR);
        graphics.fill(tailX + ss * 2 - 1, zeroY - ss, tailX + ss * 3 - 1, zeroY + ss, AXIS_COLOR);
        graphics.fill(tailX + ss * 3 - 1, zeroY - ss / 2, tailX + ss * 4 - 1, zeroY + ss / 2, AXIS_COLOR);

        // 刻度：每 10px 一条（0..60），值 = v×3.2（个物品/tick，1 tick = 64 个物品 × 0.05s），
        // 位数超 4 走科学计数法（formatAxisLabel），右对齐 Y 轴左缘，2x 放大绘制（缩小后 4px 高）
        for (int step = 0; step <= VT_H / 10; step++) {
            int p = step * 10;
            double v = vmaxFShow - span * p / VT_H;
            String label = formatAxisLabel(v * 3.2);
            int textW = this.font.width(label);
            int tx = axisX - VT_TICK_GAP * ss - textW * 2;
            int ty = p * ss - ss * 2;
            drawScaledText(graphics, label, tx, ty, 2.0F);
        }
        // 单位标注：/tick，Y 轴顶端右侧
        drawScaledText(graphics, "/tick", axisX + ss + ss - 1, 0, 2.0F);

        graphics.pose().popPose();
    }

    /**
     * 在超采样坐标系内以指定倍率绘制 Minecraft 位图文字
     * <p>
     * 刻度文字约定：2x 放大绘制（16px 超采样像素），随图表区整体缩 1/4
     * 后显示 4px 高——即 8px 位图缩 2 倍，正好合适（用户确认）
     *
     * @param graphics 渲染器
     * @param text     文字内容
     * @param x        绘制起点（超采样坐标）
     * @param y        绘制起点（超采样坐标）
     * @param scale    放大倍率（2 = 16px 超采样像素）
     */
    private void drawScaledText(GuiGraphics graphics, String text, int x, int y, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, AXIS_COLOR, false);
        graphics.pose().popPose();
    }

    /**
     * 刻度/读数数值格式化：两位有效数字，避免科学计数
     * <p>
     * 防御极端值（用户实测产物空槽等工况下 Q 可达 1e27 级）：
     * NaN/±inf 与 |值| ≥ 1e6 统一显示 "inf"，|值| < 1e-4 显示 "0"，
     * 其余走 %.3g（3 位有效数字，含 e 时回退 %.3f）——
     * 速率显示精度要求（实测 0.05 阈值 + 2 位有效数字下小速率
     * 恒显示 0.0，无法分辨 0.01 与 0.049 的差异）
     *
     * @param value 数值（通量或浓度商）
     * @return 显示文本
     */
    private static String formatTickValue(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) >= 1e6) {
            return "inf";
        }
        if (value != 0.0 && Math.abs(value) < 1e-4) {
            return "0";
        }
        String s = String.format("%.3g", value);
        if (s.contains("e")) {
            s = String.format("%.3f", value);
        }
        return s;
    }

    /** 上标数字（科学计数法指数用，Minecraft 字体含 unifont 字形） */
    private static final char[] SUPERSCRIPT_DIGITS = {'⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹'};

    /** 上标负号（U+207B） */
    private static final char SUPERSCRIPT_MINUS = '⁻';

    /**
     * v-t 图 Y 轴刻度标注格式化
     * <p>
     * 分档显示（各档文本宽度大致相等，刻度区美观）：
     * <ul>
     *   <li>|值| ≥ 10000（5 位及以上，4 位有效数字装不下）→ 2 位有效
     *       数字 + 科学计数法，指数上标（如 1.2×10⁴、1×10⁴）</li>
     *   <li>1 ≤ |值| < 10000 → 4 位有效数字（如 1124、1.000、999.9），
     *       尾随零保留（旧 %.3f 会把 1124 显示成 "1124.000" 撑爆）</li>
     *   <li>|值| < 1 → 3 位小数能正常显示（四舍五入后非 0.000，即值
     *       ≥ ~0.0005）时保留 3 位小数（如 0.056、0.050、0.334），
     *       尾随零保留；值小到 3 位小数显示为 0.000（如 0.0005）时改
     *       2 位有效数字 + 科学计数法（如 5×10⁻⁴）</li>
     * </ul>
     *
     * @param value 刻度值（个/tick 口径）
     * @return 显示文本
     */
    private static String formatAxisLabel(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) >= 1e6) {
            return "inf";
        }
        if (value == 0.0) {
            return "0";
        }
        double abs = Math.abs(value);
        if (abs >= 10000.0) {
            return scientific(value);
        }
        if (abs < 1.0) {
            // 3 位小数能正常显示（四舍五入后非 0.000，即值 ≥ ~0.0005）
            // → 保留 3 位小数（如 0.056、0.050、0.334，尾随零保留）；
            // 值小到 3 位小数显示为 0.000（rint 银行家舍入，0.0005 → 0）
            // → 2 位有效数字科学计数法（如 0.0005 → 5×10⁻⁴）
            if (Math.rint(value * 1000.0) != 0.0) {
                return String.format("%.3f", value);
            }
            return scientific(value);
        }
        // 1 ≤ |值| < 10000：4 位有效数字，尾随零保留（如 1.000、1.500、
        // 999.9、1124）——先 BigDecimal HALF_UP 舍入到 4 位有效数字，
        // 溢出成 5 位整数/指数形式（如 9999.5 → 1.000E+4）转科学计数，
        // 未溢出则按"整数位数 + 补齐小数位到 4 位有效数字"格式化
        java.math.BigDecimal bd = new java.math.BigDecimal(Double.toString(value))
                .round(new java.math.MathContext(4, java.math.RoundingMode.HALF_UP));
        if (bd.toString().indexOf('E') >= 0 || bd.toString().indexOf('e') >= 0) {
            return scientific(value);
        }
        int intDigits = (int) Math.floor(Math.log10(abs)) + 1;
        int decimals = Math.max(0, 4 - intDigits);
        return String.format("%." + decimals + "f", value);
    }

    /**
     * 2 位有效数字 + 科学计数法（指数上标，如 1.2×10⁴、5.0×10⁻²）
     * <p>
     * 四舍五入进位处理：尾数舍入到 10（如 9.96 → 10.0）时指数 +1、
     * 尾数归一为 1.0，保证 mantissa 恒在 [1, 10)（显示 "1×10⁵" 而非
     * "10×10⁴"）
     *
     * @param value 待格式化数值（非 0）
     * @return 科学计数法文本
     */
    private static String scientific(double value) {
        double abs = Math.abs(value);
        int exp = (int) Math.floor(Math.log10(abs));
        String m = String.format("%.1f", value / Math.pow(10, exp));
        if (m.startsWith("10")) {
            // 尾数舍入进位：10.0 → 1.0 × 10^(exp+1)
            exp += 1;
            m = String.format("%.1f", value / Math.pow(10, exp));
        }
        if (m.endsWith(".0")) {
            m = m.substring(0, m.length() - 2);
        }
        return m + "×10" + superscript(exp);
    }

    /**
     * 整数指数 → 上标字符串（如 4 → "⁴"、-3 → "⁻³"）
     *
     * @param exp 十进制指数
     * @return 上标表示
     */
    private static String superscript(int exp) {
        StringBuilder sb = new StringBuilder();
        if (exp < 0) {
            sb.append(SUPERSCRIPT_MINUS);
            exp = -exp;
        }
        for (char c : Integer.toString(exp).toCharArray()) {
            sb.append(SUPERSCRIPT_DIGITS[c - '0']);
        }
        return sb.toString();
    }

    /**
     * 画折线段（按 x/y 步进的整数插值，双轴均分步数防断线，线宽可调）
     * <p>
     * 线宽以 (x,y) 为中心对称展开（fill 半开区间左右各 width/2），
     * 保证折线正好穿过采样点中心——早期直接 fill(x,y,x+width,y+width)
     * 使线中心偏移 width/2，视觉上未穿过点中心（已修复）
     *
     * @param graphics 渲染器
     * @param x1       起点 x（屏幕坐标）
     * @param y1       起点 y（屏幕坐标）
     * @param x2       终点 x（屏幕坐标）
     * @param y2       终点 y（屏幕坐标）
     * @param width    线宽（像素，超采样坐标系下为超采样像素）
     * @param color    线段颜色
     */
    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                 int width, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            return;
        }
        int half = width / 2;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            graphics.fill(x - half, y - half, x + width - half, y + width - half, color);
        }
    }

    /**
     * 反应区：REACTION 标签 + REV/IRR 徽章 + 反应方程式（8px 不缩放）
     * <p>
     * 方程式基于 JSON 解析分段绘制：
     * <ul>
     *   <li>系数（>1 时前缀）与物质缩写使用对应物品色（与卡片缩写同步
     *       加深 1/5）；"+" 与 =/→ 符号为纯黑</li>
     *   <li>换行：单行超过可用宽（67~188）时在 "+" / "=" / "→" 附近断开，
     *       符号放行首；浅色底高随行数增加（每行 +10px），v-t 图随之下移</li>
     *   <li>背景：无边框，只填浅色主题色（与 v-t 图背景同色系）</li>
     * </ul>
     *
     * @param graphics 渲染器
     */
    private void drawReactionArea(GuiGraphics graphics) {
        // REACTION 标签：英文大写，vanilla 8px 字体，纯黑，y 与 INPUT 上顶面对齐
        graphics.drawString(this.font, "REACTION",
                this.leftPos + REACTION_X, this.topPos + REACTION_Y, NAME_COLOR, false);

        int theme = enzymeData.color();

        // 反应类型徽章：REV（可逆）/ IRR（不可逆），主题色加深文字，
        // y 与 REACTION 上顶面对齐，x 右对齐方程式框右缘
        String revTag = enzymeData.reversible() ? "REV" : "IRR";
        graphics.drawString(this.font, revTag,
                this.leftPos + EQ_X1 - this.font.width(revTag),
                this.topPos + TAG_Y, darken(theme), false);

        // 分段构建方程式（段 = 系数/缩写/符号 + 各自颜色），
        // 与物品 tooltip 共用 EnzymeEquation 同一份构建逻辑（样式一致）
        List<EnzymeEquation.Segment> segments = EnzymeEquation.guiSegments(enzymeData);

        // 贪心换行（断点只在 + / 箭头附近），行数驱动框高与 v-t 定位
        List<List<EnzymeEquation.Segment>> rows = wrapEquation(segments);
        this.equationRowCount = rows.size();

        // 美化背景：无边框，只填浅色主题色（与 v-t 图背景同色系）
        int fillColor = lighten(theme);
        int boxX0 = this.leftPos + EQ_X0 + 1;
        int boxX1 = this.leftPos + EQ_X1 - 1;
        int boxY0 = this.topPos + EQ_BOX_Y;
        int boxY1 = this.topPos + EQ_BOX_Y + EQ_BOX_H + (rows.size() - 1) * EQ_ROW_STEP;
        graphics.fill(boxX0, boxY0, boxX1 + 1, boxY1 + 1, fillColor);

        // 每行 8px 分段绘制，各自居中于 67~188（中心 127.5）
        for (int r = 0; r < rows.size(); r++) {
            List<EnzymeEquation.Segment> row = rows.get(r);
            int rowW = 0;
            for (EnzymeEquation.Segment segment : row) {
                rowW += this.font.width(segment.text());
            }
            int rowX0 = (EQ_X0 + EQ_X1) / 2 - rowW / 2;
            int cursor = 0;
            int rowY = EQ_Y + r * EQ_ROW_STEP;
            for (EnzymeEquation.Segment segment : row) {
                graphics.drawString(this.font, segment.text(),
                        this.leftPos + rowX0 + cursor, this.topPos + rowY,
                        segment.color(), false);
                cursor += this.font.width(segment.text());
            }
        }
    }

    /**
     * 反应方程式贪心换行：断点只允许在 "+" / "=" / "→" 符号附近
     * <p>
     * 规则（逐段扫描，当前行放不下时）：
     * <ul>
     *   <li>超宽段是符号（+ 或箭头）：符号放新行首，结束当前行</li>
     *   <li>超宽段是物质且行尾是 "+"：把 "+" 移到新行首（化学式排版惯例，
     *       续行以 + 开头），物质随其后</li>
     *   <li>超宽段是物质且行尾非 "+"（理论不发生，因符号总在物质间）：直接换行</li>
     * </ul>
     * 段文本来自 EnzymeEquation（GUI 与物品 tooltip 同一份构建逻辑）
     *
     * @param segments 方程式全段序列（系数/物质/符号交替）
     * @return 行列表（每行一段段序列，保持原顺序）
     */
    private List<List<EnzymeEquation.Segment>> wrapEquation(List<EnzymeEquation.Segment> segments) {
        List<List<EnzymeEquation.Segment>> rows = new ArrayList<>();
        List<EnzymeEquation.Segment> row = new ArrayList<>();
        int rowW = 0;
        for (EnzymeEquation.Segment seg : segments) {
            int w = this.font.width(seg.text());
            boolean plus = seg.text().equals("+");
            boolean arrow = seg.text().equals("=") || seg.text().equals("→");
            if (!row.isEmpty() && rowW + w > EQ_ROW_MAX_W) {
                if (plus || arrow) {
                    // 符号放新行首
                    rows.add(row);
                    row = new ArrayList<>();
                    rowW = 0;
                } else if (row.get(row.size() - 1).text().equals("+")) {
                    // 行尾 "+" 移到新行首，物质随其后
                    EnzymeEquation.Segment lastPlus = row.remove(row.size() - 1);
                    rowW -= this.font.width("+");
                    rows.add(row);
                    row = new ArrayList<>();
                    row.add(lastPlus);
                    rowW = this.font.width("+");
                } else {
                    rows.add(row);
                    row = new ArrayList<>();
                    rowW = 0;
                }
            }
            row.add(seg);
            rowW += w;
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    /**
     * v-t 图顶部 y：随方程式行数下移（每行 +10px），保证不与框重叠
     *
     * @return v-t 图顶部 GUI 相对 y
     */
    private int vtY() {
        return VT_Y_BASE + (equationRowCount - 1) * EQ_ROW_STEP;
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
        refreshEnzymeIfChanged();
        // 未用物种槽先全部移出屏外（含无酶态全部物种槽、当前酶不用的槽位）——
        // 再让两个滚动区域各自写自己的坐标；全局清理必须在区域写入之前，
        // 否则后执行区域的"先清再写"会抹掉先执行区域的坐标（见 tick 说明）
        clearSpeciesSlotPositions();
        inputArea.tick();
        outputArea.tick();
        // v-t 采样：每 5 tick（0.25s）落一点，点为窗口平均（连续 5 tick
        // 的 flux 均值）——与服务端历史/折线图数据点同口径，平滑瞬时抖动
        vtTickCounter++;
        vtWindowSum += menu.getFlux();
        if (vtTickCounter >= VT_SAMPLE_TICKS) {
            vtTickCounter = 0;
            vtFlux[vtIndex] = vtWindowSum / VT_SAMPLE_TICKS;
            vtWindowSum = 0.0;
            vtIndex = (vtIndex + 1) % VT_POINTS;
            vtSampleCount = Math.min(vtSampleCount + 1, VT_POINTS);
        }
    }

    /**
     * 全部物种槽移出屏外（(-100,-100)）：当前酶未使用的槽位保持禁用
     * （vanilla findSlot 命中不到、渲染无残留，等效禁用）
     * <p>
     * 每 tick 在 inputArea/outputArea.tick 之前执行一次——两个区域的
     * syncSlotPositions 只写自己区域的坐标，未用槽位靠本方法兜底
     */
    private void clearSpeciesSlotPositions() {
        int base = com.github.crafteve.biocraft.blockentity.enzyme.EnzymeMachineBlockEntity.SPECIES_SLOT_BASE;
        for (int slot = base; slot < base + speciesSlotCount(); slot++) {
            menu.getSlot(slot).x = -100;
            menu.getSlot(slot).y = -100;
        }
    }

    /**
     * 滚动卡片描述：物种卡（物品槽位）或能量卡（FE 显示）
     * <p>
     * 卡片顺序 = 酶数据表 JSON 条目顺序（fe 条目在原位生成能量卡，
     * 与其他 input/output 卡片同滚动区同顺序）；容器槽位只分配给
     * 物种卡（fe 无物品槽），槽位序号与 Menu/BE 的映射规则一致
     */
    private sealed interface CardSpec permits SpeciesCard, EnergyCard {
    }

    /**
     * 物种卡：一个物品槽位（非 fe 物种）
     *
     * @param spec         物种条目（物品 id/系数/Km）
     * @param containerSlot 容器槽位序号（非 fe 连续编号）
     */
    private record SpeciesCard(EnzymeFactoryData.SpeciesSpec spec, int containerSlot) implements CardSpec {
    }

    /**
     * 能量卡：FE 显示（fe 物种，无槽位不可交互）
     *
     * @param count 化学计量系数（每分子 FE 数，容量公式入参）
     */
    private record EnergyCard(int count) implements CardSpec {
    }

    /**
     * 滚动卡片区域抽象：输入区与输出区共用一套布局/滚动/绘制/命中逻辑
     * <p>
     * 与 Menu 槽位的关系：本区域持有一段卡片描述列表（物种卡携带容器
     * 槽位序号，能量卡无槽位），卡片顺序 = JSON 条目顺序
     * <p>
     * 槽位原版化后的分工（AT 已把 1.21.1 的 Slot.x/y 拆掉 final，见
     * src/main/resources/META-INF/accesstransformer.cfg）：
     * <ul>
     *   <li>交互全交 vanilla：本类 tick 每帧把物种槽坐标按滚动偏移写入
     *       Slot.x/y（syncSlotPositions，未用槽位/无酶态移出屏外），
     *       vanilla 的 findSlot/hoveredSlot/双击收集/拖拽分裂/数字键/
     *       JEI-EMI U-R/tooltip 全部按坐标原生工作，零手动复刻</li>
     *   <li>渲染：卡片底色/槽位贴图/物品图标/高亮/缩写/进度条自绘于
     *       draw（scissor 裁剪视口）；renderSlot 对物种槽空实现防止
     *       vanilla 双重渲染，isHighlightable=false 关闭不可裁剪的
     *       vanilla 高亮</li>
     * </ul>
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

        /** 本区域卡片描述列表（物种卡 + 能量卡，JSON 顺序） */
        private final List<CardSpec> cards;

        /** 当前滚动像素偏移（渲染用，平滑插值后的显示值） */
        private double scrollOffset;

        /** 目标滚动像素偏移（滚轮事件直接更新，tick 中插值逼近） */
        private double scrollTarget;

        /**
         * @param areaX 滚动容器 x（GUI 相对）
         * @param cards 本区域卡片描述列表
         */
        CardScrollArea(int areaX, List<CardSpec> cards) {
            this.areaX = areaX;
            this.cards = cards;
        }

        /** 本区域卡片数（物种卡 + 能量卡） */
        int getCount() {
            return cards.size();
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
         * 每 tick 平滑插值：显示偏移向目标偏移逼近（差距 <0.5px 直接吸附），
         * 并把本区域物种槽坐标按滚动偏移写入对应 Slot（AT 已拆掉 Slot.x/y
         * 的 final，见 META-INF/accesstransformer.cfg）——vanilla 的命中/
         * hoveredSlot/快捷键按坐标原生工作，与绘制位置天然同步。
         * 注意：本方法只写本区域槽位，全局未用槽位的屏外清理由
         * containerTick 的 clearSpeciesSlotPositions 统一执行——
         * 若每个区域各自"先全清再写自己"，后执行区域的清理会把
         * 先执行区域的坐标抹掉（实测输入槽恒屏外、点击无效的根因）
         */
        void tick() {
            this.scrollOffset += (this.scrollTarget - this.scrollOffset) * SCROLL_LERP;
            if (Math.abs(this.scrollTarget - this.scrollOffset) < 0.5) {
                this.scrollOffset = this.scrollTarget;
            }
            syncSlotPositions();
        }

        /**
         * 将本区域卡片坐标写入对应 Slot（与 draw 的绘制公式严格一致）：
         * x = areaX + SLOT_X，y = SCROLL_Y + i×CARD_STEP − round(offset) + SLOT_Y
         * <p>
         * 不清理其他区域/未用槽位（见 tick 的说明）——未用槽位的屏外
         * 清理由 containerTick 统一执行
         */
        private void syncSlotPositions() {
            int offset = (int) Math.round(scrollOffset);
            for (int i = 0; i < getCount(); i++) {
                if (!(cards.get(i) instanceof SpeciesCard speciesCard)) {
                    continue;
                }
                Slot slot = menu.getSlot(speciesCard.containerSlot());
                slot.x = areaX + MachineMenu.SLOT_X;
                slot.y = MachineMenu.SCROLL_Y + i * MachineMenu.CARD_STEP - offset + MachineMenu.SLOT_Y;
            }
        }

        /**
         * 绘制本区域全部卡片（视口 scissor 裁剪内）：
         * 物种卡 = 卡片底色 + 槽位背景（slot.png 18×18 @卡片内 (1,2)）
         * + 缩写 + 浓度进度条 + 浓度读数——物品图标/堆叠数/拖拽分裂
         * 选取预览由 vanilla renderSlot 渲染（见 renderSlot 覆写说明，
         * 包 scissor 裁剪滚动视口）；
         * 能量卡 = 绿色 "FE" 标签 + 存量/容量 + 绿色进度条 + 产率读数
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
                if (cards.get(i) instanceof EnergyCard energyCard) {
                    drawEnergyCard(graphics, x, cardY, energyCard.count());
                    continue;
                }
                SpeciesCard speciesCard = (SpeciesCard) cards.get(i);
                // 槽位背景：slot.png 18×18 @卡片内 (1,2)（物品图标由 vanilla 渲染）
                int pngX = x + MachineMenu.SLOT_PNG_X;
                int pngY = cardY + MachineMenu.SLOT_PNG_Y;
                Slot slot = menu.getSlot(speciesCard.containerSlot());
                graphics.blit(SLOT, pngX, pngY, 0, 0, 18, 18, 18, 18);
                ItemStack stack = slot.getItem();

                // 物品数据：颜色取 substances.json 解析出的物品染色（24 位 RGB 补 alpha）
                String itemId = speciesCard.spec().item();
                MoleculeItem item = ModItems.byId(itemId).get();
                // 缩写颜色：物品色加深 1/5；与卡片底色（#C6C6C6）亮度相近时
                // 改黑色保证可读（实测 H⁺ 为纯白，灰卡上几乎不可见）
                int itemColor = cardTextColor(item.getTintColor());

                // 缩写：与槽位上顶面平齐（y = png 顶），颜色 = 物品色加深 1/5
                graphics.drawString(font, item.getAbbreviation(),
                        x + MachineMenu.SLOT_PNG_X + MachineMenu.NAME_DX,
                        pngY, itemColor, false);

                // 浓度：客户端重建引擎连续浓度 = (槽位数量 + 同步余量)/64，
                // 槽位数经菜单槽位同步、余量经 ContainerData 扩展通道同步
                // （客户端 BE 引擎浓度恒 0，直接读引擎会导致进度条/读数不显示）；
                // 上限 = MAX_CONCENTRATION（槽位 n 组 + 余量），允许"槽满仍攒余量"
                double concentration = Math.max(0.0, Math.min(
                        (stack.getCount() + menu.getRemainder(speciesCard.containerSlot())) / 64.0,
                        com.github.crafteve.biocraft.reaction.KineticConstants.MAX_CONCENTRATION));

                // 进度条：槽位下方与卡片底端之间（20..28）垂直居中，
                // 3px 高、54px 长（卡片宽 56 居中 → x+1），浅灰轨道 + 物品色填充。
                // 填充比例按 MAX_CONCENTRATION 归一化：槽位容量参数化后满堆
                // 浓度可达 n 组（2.0），若直接 54×浓度会超出 54px 轨道（过满 bug），
                // 归一化后"满堆 = 满格"（上限含余量，略高于 2.0 时满格）
                int barY = cardY + MachineMenu.SLOT_PNG_Y + 18 + (8 - 3) / 2;
                double barFill = 54.0 * concentration / com.github.crafteve.biocraft.reaction.KineticConstants.MAX_CONCENTRATION;
                graphics.fill(x + 1, barY, x + 1 + 54, barY + 3, BAR_TRACK);
                graphics.fill(x + 1, barY, x + 1 + (int) Math.min(barFill, 54), barY + 3, itemColor);

                // 浓度数据：槽位底面右侧 4px、向下偏移 1px 为文字左下角；
                // 浅灰黑文字，数值 = 浓度 × 堆叠数（连续值，允许小数）；
                // 格式防过长：整数部分 ≥3 位（满堆 128 个）时只保留 1 位小数，
                // 否则保留 2 位（"x128.00" 超卡片宽被遮挡的修复）
                int numX = pngX + MachineMenu.NAME_DX;
                int numBottomY = pngY + 18 + 1;
                double itemCount = concentration * 64.0;
                String countText = itemCount >= 100.0
                        ? String.format("%.1f", itemCount)
                        : String.format("%.2f", itemCount);
                graphics.drawString(font, "x" + countText,
                        numX, numBottomY - 8, CONC_TEXT_COLOR, false);
            }
            graphics.disableScissor();
        }

        /**
         * 绘制能量卡（56×28）：绿色 FE 主题，与物种卡同尺寸同滚动
         * <p>
         * 布局与物种卡完全对齐（贴图 + 右侧文字同位置口径）：
         * <ol>
         *   <li>槽位贴图（slot.png 18×18 @卡片内 (1,2)）：仅装饰不可交互，
         *       经 setColor 调制为绿色 tint（#4CAF50）——本卡无 Slot，纯粹为
         *       与物品卡视觉统一</li>
         *   <li>第一行 "FE"：y = 贴图顶，绿色主题色</li>
         *   <li>第二行 "xN"（存量，M/k 紧凑单位，如 5.66M）：y = 贴图底面
         *       下方 1px − 8px 字高，灰色与物品卡浓度读数同色（CONC_TEXT_COLOR）</li>
         *   <li>绿色进度条（54×3，存量/容量比例，与物种卡进度条同位置口径）</li>
         * </ol>
         * 颜色：#4CAF50 浅绿（浅灰卡片底上可读）；数据全部来自 ContainerData
         * （存量）与引擎 API（容量/换算），不复制任何公式
         *
         * @param graphics 渲染器
         * @param cardX    卡片左上 x（已含 leftPos）
         * @param cardY    卡片左上 y（已含 topPos 与滚动偏移）
         * @param count    fe 化学计量系数（每分子 FE 数）
         */
        private void drawEnergyCard(GuiGraphics graphics, int cardX, int cardY, int count) {
            int stored = menu.getEnergyStored();
            int capacity = com.github.crafteve.biocraft.reaction.EnergyKinetics.capacity(count);
            // 槽位贴图（装饰性）：与物种卡贴图同位置，绿色 tint（#4CAF50）
            int pngX = cardX + MachineMenu.SLOT_PNG_X;
            int pngY = cardY + MachineMenu.SLOT_PNG_Y;
            graphics.setColor(ENERGY_TINT_R, ENERGY_TINT_G, ENERGY_TINT_B, 1.0f);
            graphics.blit(SLOT, pngX, pngY, 0, 0, 18, 18, 18, 18);
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            // 第一行 "FE"：y 与物种卡缩写平齐（贴图顶），绿色主题色
            int textX = pngX + MachineMenu.NAME_DX;
            graphics.drawString(font, "FE", textX, pngY, ENERGY_COLOR, false);
            // 第二行 "xN"（紧凑单位）：y 与物种卡浓度读数平齐，灰色与物品卡同步
            int numY = pngY + 18 + 1 - 8;
            graphics.drawString(font, "x" + formatCompact(stored), textX, numY, CONC_TEXT_COLOR, false);
            // 中部：绿色进度条（与物种卡进度条同位置口径：卡片内 y 20..28 居中）
            int barY = cardY + MachineMenu.SLOT_PNG_Y + 18 + (8 - 3) / 2;
            int fill = capacity > 0 ? (int) (54L * stored / capacity) : 0;
            graphics.fill(cardX + 1, barY, cardX + 1 + 54, barY + 3, BAR_TRACK);
            graphics.fill(cardX + 1, barY, cardX + 1 + Math.min(fill, 54), barY + 3, ENERGY_COLOR);
        }

        /**
         * FE 数值 → 紧凑单位格式（百万 M / 千 k，两位小数，如 5664000 → "5.66M"）
         *
         * @param fe FE 数值
         * @return 紧凑字符串（<1k 原样输出）
         */
        private static String formatCompact(int fe) {
            if (fe >= 1_000_000) {
                return String.format("%.2fM", fe / 1_000_000.0);
            }
            if (fe >= 1_000) {
                return String.format("%.2fk", fe / 1_000.0);
            }
            return String.valueOf(fe);
        }
    }

    /**
     * 标题区：方块物品图标 + 缩写文本框 + displayname + INPUT/OUTPUT 标签
     * <p>
     * 主题色取自数据表 color 字段（ARGB），加深/变浅由线性混合推导：
     * 边框色 = 主题色原色（补 alpha 保险）；缩写文字色 = 主题色 × 3/5；
     * 填充色 = 主题色向白色混合 4/5（浅）
     * <p>
     * 文本框：1px 矩形框架（不倒圆角），左上 (28,10)、下沿 y=21；
     * 中轴线 15.5——框内缩写与 displayname 统一按 8px 绝对定位
     * （y = boxY + 3，实测文字整体偏上 1px 已下移修正）
     *
     * @param graphics 渲染器
     */
    private void drawTitleArea(GuiGraphics graphics) {
        // 0 槽（酶槽）占原方块图标位：物品图标由 vanilla 渲染（isActive=true），
        // 背景贴图已在 renderBg 的 drawEnzymeSlotBackground 绘制，此处不再画方块

        // 缩写文本框：1px 矩形框架（无圆角），y 范围 10~21
        drawAbbreviationBox(graphics, enzymeData.abbreviation(), enzymeData.color());

        // displayname：文本框右缘 + 4px，纯黑文字，绝对定位：
        // 中文与英文统一按 8px 处理（实测 MC 中文渲染也是 8px 高，非 16px），
        // 与缩写文本同中轴且同步下移 1px（y = boxY + 3）
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        boolean chinese = language != null && language.startsWith("zh");
        String name = chinese ? enzymeData.nameZn() : enzymeData.nameEn();
        int nameX = this.leftPos + ABBR_X + abbreviationBoxWidth(enzymeData.abbreviation()) + NAME_GAP;
        int nameY = this.topPos + ABBR_Y + 3;
        graphics.drawString(this.font, name, nameX, nameY, NAME_COLOR, false);

        // INPUT / OUTPUT 标签：英文大写，vanilla 8px 字体，纯黑
        graphics.drawString(this.font, "INPUT", this.leftPos + INPUT_X, this.topPos + INPUT_Y, NAME_COLOR, false);
        graphics.drawString(this.font, "OUTPUT", this.leftPos + OUTPUT_X, this.topPos + OUTPUT_Y, NAME_COLOR, false);
    }

    /**
     * 缩写文本框宽度（文字宽 + 内边距 + 边框 ×2）
     *
     * @param text 框内文字
     * @return 框宽像素
     */
    private int abbreviationBoxWidth(String text) {
        return this.font.width(text) + (ABBR_PAD + ABBR_BORDER) * 2;
    }

    /**
     * 绘制缩写文本框（1px 矩形框架 + 加深文字色）
     * <p>
     * 有酶态传酶缩写 + 主题色；无酶态传 [unknown] + 灰色——格式完全一致
     *
     * @param graphics 渲染器
     * @param text     框内文字
     * @param theme    主题色（边框补 alpha、文字加深、填充提亮）
     */
    private void drawAbbreviationBox(GuiGraphics graphics, String text, int theme) {
        int borderColor = theme | 0xFF000000;
        int textColor = darken(theme);
        int fillColor = lighten(theme);
        int boxW = abbreviationBoxWidth(text);
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
        graphics.drawString(this.font, text,
                boxX + ABBR_BORDER + ABBR_PAD,
                boxY + 3, textColor, false);
    }

    /**
     * 卡片文字颜色：仅白色与接近卡片底色的灰色改纯黑，其余保持物品色加深 1/5
     * <p>
     * 判定（实测修正：阈值 60 曾误伤约 80% 彩色物品，已收紧）：
     * <ul>
     *   <li>白色兜底：亮度 > 240（纯白 H⁺ 亮度 255，与卡片灰 198 差 57，
     *       用接近度阈值覆盖不到，必须按亮度上限单独判定）</li>
     *   <li>灰色：饱和度 < 40（灰/白饱和度低，排除亮青、亮黄等高饱和
     *       但亮度接近 198 的彩色）且亮度与卡片灰（198）差 < 10</li>
     * </ul>
     *
     * @param tintColor 物品染色（24 位 RGB）
     * @return 卡片文字颜色（ARGB）
     */
    private static int cardTextColor(int tintColor) {
        int r = (tintColor >> 16) & 0xFF;
        int g = (tintColor >> 8) & 0xFF;
        int b = tintColor & 0xFF;
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;
        int saturation = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
        if (luminance > 240 || (saturation < 40 && Math.abs(luminance - 198) < 10)) {
            return 0xFF000000;
        }
        return CompatRenderUtil.darkenOneFifth(tintColor);
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

