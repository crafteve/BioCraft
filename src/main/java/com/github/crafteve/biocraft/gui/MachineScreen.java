package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineCategory;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
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

    /** 反应方程式顶部 y（REACTION 内容底部 37 + 4px） */
    private static final int EQ_Y = 41;

    /** 反应方程式缩放倍率（8px 位图 ×2 = 16px 高） */
    private static final float EQ_SCALE = 2.0F;

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
    }

    /**
     * 反应区：REACTION 标签 + 反应方程式（2x 放大 16px）
     * <p>
     * 方程式基于 JSON 解析分段绘制：
     * <ul>
     *   <li>系数（>1 时前缀）与物质缩写使用对应物品色（与卡片缩写同步
     *       加深 1/5）；"+" 与 ⇌/→ 符号为纯黑</li>
     *   <li>2x 等比放大：pose 平移至绘制起点后 scale(2)，在缩放坐标系
     *       内按 8px 基准宽度逐段绘制，实际渲染 16px 高（位图 2x 放大
     *       可能有轻微模糊，为等比放大的固有代价）</li>
     *   <li>整体居中于 x 67~188（中心 127.5），顶部 y=41
     *       （REACTION 内容底部 37 + 4px）</li>
     * </ul>
     *
     * @param graphics 渲染器
     */
    private void drawReactionArea(GuiGraphics graphics) {
        // REACTION 标签：英文大写，vanilla 8px 字体，纯黑，y 与 INPUT 上顶面对齐
        graphics.drawString(this.font, "REACTION",
                this.leftPos + REACTION_X, this.topPos + REACTION_Y, NAME_COLOR, false);

        // 分段构建方程式（段 = 系数/缩写/符号 + 各自颜色）
        List<EqSegment> segments = new ArrayList<>();
        appendEqSide(segments, enzymeData.reactants());
        segments.add(new EqSegment(enzymeData.reversible() ? "⇌" : "→", NAME_COLOR));
        appendEqSide(segments, enzymeData.products());

        // 总宽（8px 基准）→ 居中于 67~188
        int totalW = 0;
        for (EqSegment segment : segments) {
            totalW += this.font.width(segment.text());
        }
        int x0 = (EQ_X0 + EQ_X1) / 2 - totalW / 2;

        // 2x 放大绘制：translate 到起点（不缩放起点坐标），scale(2) 放大字形
        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos + x0, this.topPos + EQ_Y, 0);
        graphics.pose().scale(EQ_SCALE, EQ_SCALE, 1.0F);
        int cursor = 0;
        for (EqSegment segment : segments) {
            graphics.drawString(this.font, segment.text(), cursor, 0, segment.color(), false);
            cursor += this.font.width(segment.text());
        }
        graphics.pose().popPose();
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
