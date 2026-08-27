package com.github.crafteve.biocraft.gui.sequence;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.sequence.SeqStepState;
import com.github.crafteve.biocraft.blockentity.sequence.SequenceMachineKind;
import com.github.crafteve.biocraft.blockentity.sequence.operation.FolderOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.TranslatorOperation;
import com.github.crafteve.biocraft.central.Codec;
import com.github.crafteve.biocraft.compat.CompatRenderUtil;
import com.github.crafteve.biocraft.gui.base.BiocraftSlot;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.item.SequenceData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 序列机通用屏幕基类（256×256 窗口，布局由 SequenceLayout 数据驱动）
 * <p>
 * 框架差异全部收敛到 {@link SequenceLayout}：背景贴图（gui_stage/gui_console）、
 * 输出卡方向（右竖滚/底横滚）、中央区标签、动画区矩形、状态栏进度条、
 * 右上角状态文字与催化剂图标——renderBg 据此一次画完全部家常逻辑。
 * 子类只覆写 {@link #renderMachineAnimation} 画自己的动画内容
 * （解旋双螺旋/转录模板mRNA配对/翻译密码子肽链/装载口袋），以及机器专属
 * 部件（编码器编辑器、各机按钮）。
 * <p>
 * 布局要点（对齐酶工厂惯例）：
 * <ul>
 *   <li>状态栏：机器名（y13）+ 状态 + 细进度条（y22-25）；转录仪/翻译机因
 *       顶栏 9,8 槽位标题右移至 x28 并补槽位底纹</li>
 *   <li>标签：INPUT (9,30)；CONSOLE族 OUTPUT (70,132)，STAGE族 OUTPUT (195,30)
 *       + 中央 LOAD/UNWIND (109,30)</li>
 *   <li>输入滚动卡片区 (7,41) 56×112 纵向；输出按布局横滚或右竖排</li>
 *   <li>动画区面板骨架：深色底 + 顶部 1px 亮线 + 淡网格（0x08FFFFFF，四边
 *       6px 边距）+ 左上标题 + 右上状态文字与图标；编码器 plainPanel 跳过骨架</li>
 *   <li>renderLabels 空实现（vanilla 标题/物品栏标识全部移除，文字全自绘）</li>
 * </ul>
 */
public class SequenceMachineScreen extends AbstractContainerScreen<SequenceMachineMenu> {

    protected static final ResourceLocation SLOT_TEX = ResourceLocation.fromNamespaceAndPath(
            BioCraft.MODID, "textures/gui/slot.png");

    /** 卡片底色（与酶工厂一致） */
    protected static final int CARD_COLOR = 0xFFC6C6C6;
    /** 深色动画区面板底色 */
    protected static final int EDIT_PANEL_COLOR = 0xFF1E1E22;
    /** 文字颜色（酶工厂 NAME_COLOR 纯黑） */
    protected static final int NAME_COLOR = 0xFF000000;
    protected static final int CONC_TEXT_COLOR = 0xFF3A3A3A;
    /** 进度条 */
    protected static final int BAR_TRACK = 0xFFB0B0B0;
    /** 碱基四色（A红/T黄/C蓝/G绿，动画与序列卡共用；U 与 T 同黄） */
    protected static final int BASE_A = 0xFFE74C3C;
    protected static final int BASE_T = 0xFFF1C40F;
    protected static final int BASE_C = 0xFF3498DB;
    protected static final int BASE_G = 0xFF2ECC71;

    private static final double SCROLL_LERP = 0.35;
    private static final double SCROLL_PIXELS_PER_NOTCH = 8.0;

    /** 输入卡片（纵向滚动）：槽位 + 固定展示的物品 */
    protected record InputCard(int containerSlot, String itemId) {
    }

    /** 输出卡片：槽位 + 固定展示的物品 + 卡片宽 + 内容样式（CardStyle） */
    protected record OutputCard(int containerSlot, String itemId, int cardWidth, SequenceSlotSpec.CardStyle style) {
    }

    protected final List<InputCard> inputCards;
    protected final List<OutputCard> outputCards;

    protected double inputScrollOffset;
    protected double inputScrollTarget;
    protected double outputScrollOffset;
    protected double outputScrollTarget;

    public SequenceMachineScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SequenceMachineMenu.WINDOW_W;
        this.imageHeight = SequenceMachineMenu.WINDOW_H;
        this.inputCards = buildInputCards(menu.getKind());
        this.outputCards = buildOutputCards(menu.getKind());
        // 立即同步滚动槽坐标（酶工厂同款修复：CardScrollArea 构造后先 tick 一次）——
        // 否则首帧渲染发生在 containerTick 之前，槽位停在 Menu 占位坐标
        // （5 个输入槽叠在同一位置"闪一下"再摊开，实测现象）
        tickScrolls();
    }

    /** 当前帧的 partialTick（0~1 帧内小数，动画钩子做帧内插值用） */
    protected float animPartialTick;

    /** 当前机器的布局描述（框架差异的单一事实源） */
    protected SequenceLayout layout() {
        return SequenceLayout.of(menu.getKind());
    }

    private List<InputCard> buildInputCards(SequenceMachineKind kind) {
        List<InputCard> cards = new ArrayList<>();
        List<SequenceSlotSpec.Slot> slots = SequenceSlotSpec.of(kind).slots();
        for (int i = 0; i < slots.size(); i++) {
            SequenceSlotSpec.Slot slot = slots.get(i);
            if (slot.role() == SequenceSlotSpec.Role.INPUT_SCROLL) {
                cards.add(new InputCard(i, slot.itemId()));
            }
        }
        return cards;
    }

    private List<OutputCard> buildOutputCards(SequenceMachineKind kind) {
        List<OutputCard> cards = new ArrayList<>();
        List<SequenceSlotSpec.Slot> slots = SequenceSlotSpec.of(kind).slots();
        for (int i = 0; i < slots.size(); i++) {
            SequenceSlotSpec.Slot slot = slots.get(i);
            if (slot.role() == SequenceSlotSpec.Role.OUTPUT_CARD) {
                cards.add(new OutputCard(i, slot.itemId(), slot.width(), slot.style()));
            }
        }
        return cards;
    }

    // ------------------------------------------------------------------
    // tick：双滚动区插值 + 槽位坐标同步（AT 已拆 Slot.x/y final）
    // ------------------------------------------------------------------

    @Override
    public void containerTick() {
        super.containerTick();
        tickScrolls();
    }

    private void tickScrolls() {
        // 输入（纵向，所有布局一致）
        if (!inputCards.isEmpty()) {
            this.inputScrollOffset += (this.inputScrollTarget - this.inputScrollOffset) * SCROLL_LERP;
            if (Math.abs(this.inputScrollTarget - this.inputScrollOffset) < 0.5) {
                this.inputScrollOffset = this.inputScrollTarget;
            }
            int vOffset = (int) Math.round(inputScrollOffset);
            // 输入纵向滚动：完全滚出可视边界的槽位移至屏外 (-1000,-1000)，
            // 原版 findSlot 按槽位坐标命中（Scissor 只裁渲染、不影响命中），
            // 不挪走则"渲染被裁但还能点"；半露的槽保留原位（部分可见应可交互）
            for (int i = 0; i < inputCards.size(); i++) {
                Slot slot = menu.getSlot(inputCards.get(i).containerSlot());
                int cardY = SequenceMachineMenu.INPUT_SCROLL_Y + i * SequenceMachineMenu.CARD_STEP
                        - vOffset;
                if (cardY + SequenceMachineMenu.CARD_H < SequenceMachineMenu.INPUT_SCROLL_Y
                        || cardY > SequenceMachineMenu.INPUT_SCROLL_Y + SequenceMachineMenu.INPUT_SCROLL_H) {
                    slot.x = -1000;
                    slot.y = -1000;
                } else {
                    slot.x = SequenceMachineMenu.INPUT_SCROLL_X + SequenceMachineMenu.SLOT_X;
                    slot.y = cardY + SequenceMachineMenu.SLOT_Y;
                }
            }
        }
        // 输出：STAGE族右竖排（固定坐标，≤3 卡无需滚动），CONSOLE族底横滚
        if (!outputCards.isEmpty()) {
            if (layout().outputVertical()) {
                for (int i = 0; i < outputCards.size(); i++) {
                    Slot slot = menu.getSlot(outputCards.get(i).containerSlot());
                    slot.x = 193 + SequenceMachineMenu.SLOT_X;
                    slot.y = 41 + i * SequenceMachineMenu.CARD_STEP + SequenceMachineMenu.SLOT_Y;
                }
                return;
            }
            this.outputScrollOffset += (this.outputScrollTarget - this.outputScrollOffset) * SCROLL_LERP;
            if (Math.abs(this.outputScrollTarget - this.outputScrollOffset) < 0.5) {
                this.outputScrollOffset = this.outputScrollTarget;
            }
            int hOffset = (int) Math.round(outputScrollOffset);
            int cardX = SequenceMachineMenu.OUT_X;
            // 输出横向滚动：与输入同机制，完全滚出可视区（OUT_W 宽）的槽移屏外挡交互
            for (int i = 0; i < outputCards.size(); i++) {
                OutputCard card = outputCards.get(i);
                Slot slot = menu.getSlot(card.containerSlot());
                int thisCardX = cardX - hOffset;
                if (thisCardX + card.cardWidth() < SequenceMachineMenu.OUT_X
                        || thisCardX > SequenceMachineMenu.OUT_X + SequenceMachineMenu.OUT_W) {
                    slot.x = -1000;
                    slot.y = -1000;
                } else {
                    slot.x = thisCardX + SequenceMachineMenu.SLOT_X;
                    slot.y = SequenceMachineMenu.OUT_Y + SequenceMachineMenu.SLOT_Y;
                }
                cardX += card.cardWidth() + SequenceMachineMenu.CARD_GAP;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        if (inArea(lx, ly, SequenceMachineMenu.INPUT_SCROLL_X, SequenceMachineMenu.INPUT_SCROLL_Y,
                SequenceMachineMenu.INPUT_SCROLL_W, SequenceMachineMenu.INPUT_SCROLL_H)) {
            scrollInput(verticalAmount);
            return true;
        }
        if (layout().outputVertical()) {
            if (inArea(lx, ly, 193, 41, 56, 112)) {
                scrollInput(verticalAmount);
                return true;
            }
        } else if (inArea(lx, ly, SequenceMachineMenu.OUT_X, SequenceMachineMenu.OUT_Y,
                SequenceMachineMenu.OUT_W, SequenceMachineMenu.OUT_H)) {
            scrollOutput(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static boolean inArea(double lx, double ly, int x, int y, int w, int h) {
        return lx >= x && lx < x + w && ly >= y && ly < y + h;
    }

    private void scrollInput(double verticalAmount) {
        int maxScroll = Math.max(0, inputCards.size() * SequenceMachineMenu.CARD_STEP
                - SequenceMachineMenu.CARD_GAP - SequenceMachineMenu.INPUT_SCROLL_H);
        this.inputScrollTarget = Math.max(0,
                Math.min(inputScrollTarget - verticalAmount * SCROLL_PIXELS_PER_NOTCH, maxScroll));
    }

    private void scrollOutput(double verticalAmount) {
        int maxScroll = Math.max(0, outputContentWidth() - SequenceMachineMenu.OUT_W);
        this.outputScrollTarget = Math.max(0,
                Math.min(outputScrollTarget - verticalAmount * SCROLL_PIXELS_PER_NOTCH, maxScroll));
    }

    private int outputContentWidth() {
        int w = 0;
        for (OutputCard card : outputCards) {
            w += card.cardWidth() + SequenceMachineMenu.CARD_GAP;
        }
        return Math.max(0, w - SequenceMachineMenu.CARD_GAP);
    }

    // ------------------------------------------------------------------
    // 渲染：renderBg 按布局一次画完全部家常逻辑，动画内容留给子类钩子
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        this.animPartialTick = partialTick;
        SequenceLayout L = layout();
        graphics.blit(L.bg(), leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        drawStatusBar(L, graphics);
        drawLabels(L, graphics);
        drawInputCards(graphics);
        if (L.outputVertical()) {
            drawVerticalOutputCards(graphics);
        } else {
            drawOutputCards(graphics);
        }
        drawAnimFrame(L, graphics);
        renderMachineAnimation(graphics, leftPos + L.ax(), topPos + L.ay(), L.aw(), L.ah());
    }

    /**
     * 动画区内容钩子（子类覆写）：传入动画区矩形（GUI 绝对坐标），
     * 调用时面板骨架（底色/网格/标题/状态/图标）已画好，内容从 y+18 以下布局
     */
    protected void renderMachineAnimation(GuiGraphics graphics, int x, int y, int w, int h) {
    }

    /** 状态栏：机器名（顶栏槽位机型右移至 x28 并补槽底纹）+ 状态 + 细进度条 */
    private void drawStatusBar(SequenceLayout L, GuiGraphics graphics) {
        boolean topSlot = menu.getKind() == SequenceMachineKind.TRANSCRIBER
                || menu.getKind() == SequenceMachineKind.TRANSLATOR;
        graphics.drawString(this.font, this.title, this.leftPos + (topSlot ? 28 : 8),
                this.topPos + 13, NAME_COLOR, false);
        if (topSlot) {
            graphics.blit(SLOT_TEX, this.leftPos + 8, this.topPos + 7, 0, 0, 18, 18, 18, 18);
        }
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        // 进行中：动词 + 进度（total==1 的单步机只显示动词，避免 "RUN 0/1" 噪音）
        String status = switch (SeqStepState.Stage.values()[Math.min(stage, SeqStepState.Stage.values().length - 1)]) {
            case IDLE -> "IDLE";
            case EXTENDING -> total > 1 ? L.verb() + " " + position + "/" + total : L.verb();
            case DONE -> "DONE";
        };
        graphics.drawString(this.font, status,
                this.leftPos + imageWidth - 8 - this.font.width(status),
                this.topPos + 13, CONC_TEXT_COLOR, false);
        if (L.hasProgressBar()) {
            // 顶栏槽位机型进度条左边界右移一个槽位宽（18px），避免与 9,8 槽位重叠
            int barLeft = leftPos + (topSlot ? 26 : 8);
            int fill = total > 0 ? (int) ((imageWidth - 16 - (topSlot ? 18 : 0)) * position / (double) total) : 0;
            graphics.fill(barLeft, topPos + 22, leftPos + 8 + imageWidth - 16, topPos + 25, BAR_TRACK);
            if (fill > 0) {
                graphics.fill(barLeft, topPos + 22, barLeft + fill, topPos + 25, 0xFF4CAF50);
            }
        }
    }

    /** 标签组：INPUT 固定 (9,30)；输出按布局，STAGE族加中央 LOAD/UNWIND 标签 */
    private void drawLabels(SequenceLayout L, GuiGraphics graphics) {
        graphics.drawString(this.font, "INPUT", this.leftPos + 9, this.topPos + 30, NAME_COLOR, false);
        if (L.outputVertical()) {
            graphics.drawString(this.font, "OUTPUT", this.leftPos + 195, this.topPos + 30, NAME_COLOR, false);
            if (!L.centerLabel().isEmpty()) {
                graphics.drawString(this.font, L.centerLabel(), this.leftPos + 109, this.topPos + 30, NAME_COLOR, false);
            }
        } else {
            graphics.drawString(this.font, "OUTPUT", this.leftPos + SequenceMachineMenu.OUTPUT_LABEL_X,
                    this.topPos + SequenceMachineMenu.OUTPUT_LABEL_Y, NAME_COLOR, false);
        }
    }

    /**
     * 动画区面板骨架：深色底 + 顶部 1px 亮线 + 淡网格（全机统一）。
     * plainPanel（编码器编辑器）只铺底色与顶线；panelTitle 为空（STAGE族）
     * 画网格但不画顶部文字标识（标题/状态/图标）——动画内容填满整个面板
     */
    private void drawAnimFrame(SequenceLayout L, GuiGraphics graphics) {
        int x = leftPos + L.ax();
        int y = topPos + L.ay();
        int w = L.aw();
        int h = L.ah();
        graphics.fill(x, y, x + w, y + h, EDIT_PANEL_COLOR);
        graphics.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
        if (L.plainPanel()) {
            return;
        }
        boolean hasText = !L.panelTitle().isEmpty();
        // 网格：有标题行时从 y+12 起（让出标题行），无标题行（STAGE族）近顶铺满
        int gridTop = hasText ? y + 12 : y + 6;
        for (int gx = x + 12; gx < x + w; gx += 14) {
            graphics.fill(gx, gridTop, gx + 1, y + h - 6, 0x08FFFFFF);
        }
        for (int gy = y + (hasText ? 18 : 12); gy < y + h; gy += 14) {
            graphics.fill(x + 6, gy, x + w - 6, gy + 1, 0x08FFFFFF);
        }
        // 状态文字（待机/进度/完成）与标题解耦：STAGE族无标题无图标，但右上角
        // 状态文字保留（此前误绑在标题条件上被连带删除）
        String status = panelStatus();
        int iconX = x + w - 18;
        if (!status.isEmpty()) {
            graphics.drawString(font, status, iconX - 6 - font.width(status), y + 7, 0xFF9E9E9E, false);
        }
        if (hasText) {
            graphics.drawString(font, L.panelTitle(), x + 6, y + 6, 0xFFE0E0E0, false);
        }
        if (L.iconChar() != ' ') {
            graphics.fill(iconX, y + 6, iconX + 10, y + 16, L.iconOuter());
            graphics.fill(iconX + 1, y + 7, iconX + 9, y + 15, L.iconInner());
            graphics.drawString(font, String.valueOf(L.iconChar()), iconX + 3, y + 7, 0xFFFFFFFF, false);
        }
    }

    /** 动画区内右上角状态词（与标题栏状态同源）：待机 / pos/total / 完成 */
    private String panelStatus() {
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        return switch (SeqStepState.Stage.values()[Math.min(stage, SeqStepState.Stage.values().length - 1)]) {
            case IDLE -> "待机";
            case EXTENDING -> total > 0 ? pos + "/" + total : "";
            case DONE -> "完成";
        };
    }

    /** 输入滚动卡片（纵向，元素照抄酶工厂：底色 + slot.png + 彩色缩写 + 进度条 + x数量） */
    protected void drawInputCards(GuiGraphics graphics) {
        if (inputCards.isEmpty()) {
            return;
        }
        int areaX = leftPos + SequenceMachineMenu.INPUT_SCROLL_X;
        int areaY = topPos + SequenceMachineMenu.INPUT_SCROLL_Y;
        graphics.enableScissor(areaX, areaY, areaX + SequenceMachineMenu.INPUT_SCROLL_W,
                areaY + SequenceMachineMenu.INPUT_SCROLL_H);
        int vOffset = (int) Math.round(inputScrollOffset);
        for (int i = 0; i < inputCards.size(); i++) {
            InputCard card = inputCards.get(i);
            int cardY = areaY + i * SequenceMachineMenu.CARD_STEP - vOffset;
            Slot slot = menu.getSlot(card.containerSlot());
            drawStockCard(graphics, areaX, cardY, SequenceMachineMenu.CARD_W,
                    SequenceMachineMenu.CARD_H, card.itemId(), slot, true);
        }
        graphics.disableScissor();
    }

    /** 输出横向滚动卡片（CONSOLE族）：序列卡（DNA/mRNA）、多肽卡、库存卡 */
    protected void drawOutputCards(GuiGraphics graphics) {
        if (outputCards.isEmpty()) {
            return;
        }
        int areaX = leftPos + SequenceMachineMenu.OUT_X;
        int areaY = topPos + SequenceMachineMenu.OUT_Y;
        graphics.enableScissor(areaX, areaY, areaX + SequenceMachineMenu.OUT_W,
                areaY + SequenceMachineMenu.OUT_H);
        int hOffset = (int) Math.round(outputScrollOffset);
        int cardX = areaX;
        for (int i = 0; i < outputCards.size(); i++) {
            OutputCard card = outputCards.get(i);
            int thisCardX = cardX - hOffset;
            Slot slot = menu.getSlot(card.containerSlot());
            switch (card.style()) {
                case DNA -> drawDnaCard(graphics, thisCardX, areaY, card.cardWidth(),
                        SequenceMachineMenu.OUT_CARD_H, slot);
                case PEPTIDE -> drawPeptideCard(graphics, thisCardX, areaY, card.cardWidth(),
                        SequenceMachineMenu.OUT_CARD_H, slot);
                case NONE, STOCK -> drawStockCard(graphics, thisCardX, areaY, card.cardWidth(),
                        SequenceMachineMenu.OUT_CARD_H, card.itemId(), slot, false);
            }
            cardX += card.cardWidth() + SequenceMachineMenu.CARD_GAP;
        }
        graphics.disableScissor();
    }

    /** 输出右竖排卡片（STAGE族）：固定坐标 193,41 起纵向排列（≤3 卡无需滚动） */
    protected void drawVerticalOutputCards(GuiGraphics graphics) {
        if (outputCards.isEmpty()) {
            return;
        }
        int areaX = leftPos + 193;
        int areaY = topPos + 41;
        graphics.enableScissor(areaX, areaY, areaX + 56, areaY + 112);
        for (int i = 0; i < outputCards.size(); i++) {
            OutputCard card = outputCards.get(i);
            Slot slot = menu.getSlot(card.containerSlot());
            drawStockCard(graphics, areaX, areaY + i * SequenceMachineMenu.CARD_STEP,
                    56, 28, card.itemId(), slot, false);
        }
        graphics.disableScissor();
    }

    /**
     * 库存卡片（输入/输出副产物通用）：
     * 输入：count - remainder（消耗时单调递减，余量为待扣部分）；输出：count + remainder（产出时单调递增）
     * <p>
     * 缩写/颜色优先取槽内实际物品（动态产物如 aa-tRNA 每槽物品可变），
     * 空槽回退到卡片注册的 itemId 展示
     */
    protected void drawStockCard(GuiGraphics graphics, int cardX, int cardY, int cardW, int cardH,
                                 String itemId, Slot slot, boolean isInput) {
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, CARD_COLOR);
        int pngX = cardX + SequenceMachineMenu.SLOT_PNG_X;
        int pngY = cardY + SequenceMachineMenu.SLOT_PNG_Y;
        graphics.blit(SLOT_TEX, pngX, pngY, 0, 0, 18, 18, 18, 18);
        ItemStack stack = slot.getItem();
        var deferred = ModItems.byId(itemId);
        MoleculeItem registered = deferred != null ? deferred.get() : null;
        int tint;
        String abbr;
        if (!stack.isEmpty() && stack.getItem() instanceof MoleculeItem mi) {
            tint = mi.getTintColor();
            abbr = mi.getAbbreviation();
        } else if (!stack.isEmpty() && stack.getItem() instanceof com.github.crafteve.biocraft.item.SequenceItem si) {
            tint = si.getTintColor();
            abbr = si.getAbbreviation();
        } else if (!stack.isEmpty() && stack.getItem() instanceof com.github.crafteve.biocraft.item.EnzymeItem ei) {
            tint = ei.getTintColor();
            abbr = ei.getAbbreviation();
        } else if (registered != null) {
            tint = registered.getTintColor();
            abbr = registered.getAbbreviation();
        } else if ("trna".equals(itemId)) {
            tint = 0xB0C4DE;
            abbr = "tRNA";
        } else if ("polypeptide".equals(itemId)) {
            var poly = ModItems.POLYPEPTIDE.get();
            tint = poly.getTintColor();
            abbr = poly.getAbbreviation();
        } else if ("misfolded_protein".equals(itemId)) {
            var mf = ModItems.MISFOLDED_PROTEIN.get();
            tint = mf.getTintColor();
            abbr = mf.getAbbreviation();
        } else {
            tint = 0xCCCCCC;
            abbr = itemId;
        }
        // 折叠机分支：悬念期输出硬编码 UNKN 灰、进度条固定绿、卡内滚动
        if (menu.getKind() == SequenceMachineKind.FOLDER) {
            int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
            boolean extending = stage == SeqStepState.Stage.EXTENDING.ordinal();
            int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
            int tot = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
            // 悬念：读条期间输出卡强制 UNKN 灰色（硬编码，不新增物品）
            String displayAbbr = abbr;
            int displayTint = tint;
            int displayColor = stack.isEmpty() ? CONC_TEXT_COLOR : cardTextColor(tint);
            if (!isInput && extending) {
                displayAbbr = "UNKN";
                displayTint = 0xFF707070;
                displayColor = 0xFF707070;
            }
            graphics.drawString(font, displayAbbr, pngX + 18 + 4, pngY, displayColor, false);
            // 进度条固定绿色
            int barY2;
            int barH2;
            if (cardH >= 25) {
                barY2 = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + (8 - 3) / 2;
                barH2 = 3;
            } else {
                barY2 = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + 1;
                barH2 = 2;
            }
            int fill2 = tot > 0 ? (int) Math.min((cardW - 2) * pos / (double) tot, cardW - 2) : 0;
            graphics.fill(cardX + 1, barY2, cardX + 1 + cardW - 2, barY2 + barH2, BAR_TRACK);
            if (fill2 > 0) {
                graphics.fill(cardX + 1, barY2, cardX + 1 + fill2, barY2 + barH2, 0xFF4CAF50);
            }
            // 卡内第二行滚动：输入=氨基酸单字母滚动，输出=解码代码滚动（控制字符过滤）
            if (extending) {
                ItemStack inStack = menu.getSlot(FolderOperation.SLOT_IN_POLYPEPTIDE).getItem();
                SequenceData inData = inStack.get(ModDataComponents.SEQUENCE.get());
                String inSeq = inData != null ? inData.seq() : "";
                if (isInput) {
                    // 输入卡：单字母氨基酸滚动（7px/字符，卡内可用宽 cardW-34）
                    int textY2 = pngY + 11;
                    int avail = cardW - 34;
                    int window = Math.max(1, avail / 7);
                    if (!inSeq.isEmpty()) {
                        int from = Math.max(0, Math.min(pos - window / 2, inSeq.length() - window));
                        int baseX = pngX + 18 + 4;
                        for (int i = from; i < Math.min(inSeq.length(), from + window); i++) {
                            char aa1 = inSeq.charAt(i);
                            int c = (i == pos && pos < tot) ? 0xFF000000 : cardTextColor(aaColor(aa1));
                            if (i == pos && pos < tot) {
                                graphics.fill(baseX - 1, textY2 - 1, baseX + 7, textY2 + 9, 0xFFFFFFFF);
                            }
                            graphics.drawString(font, String.valueOf(aa1), baseX, textY2, c, false);
                            baseX += 7;
                        }
                        if (inSeq.length() > window) {
                            graphics.drawString(font, "…", baseX, textY2, 0xFF90A4AE, false);
                        }
                    } else {
                        graphics.drawString(font, "空", pngX + 18 + 4, pngY + 11, CONC_TEXT_COLOR, false);
                    }
                } else {
                    // 输出卡：解码代码滚动（6px/字符，控制字符替换为空格）
                    String program = "";
                    if (!inSeq.isEmpty()) {
                        var decoded = Codec.decodeFromPolypeptide(inSeq);
                        if (decoded.ok()) program = sanitizeProgram(decoded.text());
                    }
                    int textY2 = pngY + 11;
                    int avail = cardW - 34;
                    int win = Math.max(1, avail / 6);
                    if (!program.isEmpty()) {
                        int pFrom = Math.max(0, Math.min(pos - win / 2, program.length() - win));
                        int baseX = pngX + 18 + 4;
                        for (int i = pFrom; i < Math.min(program.length(), pFrom + win); i++) {
                            char ch = program.charAt(i);
                            int c = (i == pos && pos < tot) ? 0xFF000000 : 0xFFB0BEC5;
                            if (i == pos && pos < tot) {
                                graphics.fill(baseX - 1, textY2 - 1, baseX + 7, textY2 + 9, 0xFF00E5FF);
                            }
                            graphics.drawString(font, String.valueOf(ch), baseX, textY2, c, false);
                            baseX += 6;
                        }
                        if (program.length() > win) {
                            graphics.drawString(font, "…", baseX, textY2, 0xFF90A4AE, false);
                        }
                    } else if (!inSeq.isEmpty()) {
                        graphics.drawString(font, "…解码中", pngX + 18 + 4, textY2, 0xFF90A4AE, false);
                    } else {
                        graphics.drawString(font, "空", pngX + 18 + 4, textY2, CONC_TEXT_COLOR, false);
                    }
                }
            } else {
                String countText2 = pos + "/" + tot;
                graphics.drawString(font, countText2, pngX + 18 + 4, pngY + 18 + 1 - 8, CONC_TEXT_COLOR, false);
            }
            return;
        }
        int color = stack.isEmpty() ? CONC_TEXT_COLOR : cardTextColor(tint);
        graphics.drawString(font, abbr, pngX + 18 + 4, pngY, color, false);
        double rem = menu.getRemainder(slot.index);
        double totalCount = isInput ? Math.max(0, stack.getCount() - rem) : stack.getCount() + rem;
        // 进度条：宽 cardW-2，位置按卡片高度分档——
        // 输入卡（28 高）与酶工厂完全同布局：3px 高，贴图底与卡底之间
        // 垂直居中（y = 贴图底 + (8-3)/2 = 22）；
        // 输出压缩卡（23 高）：贴图底 +1px、2px 高贴卡底（空间不足）
        int barY;
        int barH;
        if (cardH >= 25) {
            barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + (8 - 3) / 2;
            barH = 3;
        } else {
            barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + 1;
            barH = 2;
        }
        int fill = (int) Math.min((cardW - 2) * totalCount / 64.0, cardW - 2);
        graphics.fill(cardX + 1, barY, cardX + 1 + cardW - 2, barY + barH, BAR_TRACK);
        if (fill > 0) {
            graphics.fill(cardX + 1, barY, cardX + 1 + fill, barY + barH, color);
        }
        // x数量（酶工厂格式：≥100 一位小数，否则两位）
        String countText = totalCount >= 100.0
                ? String.format("%.1f", totalCount)
                : String.format("%.2f", totalCount);
        graphics.drawString(font, "x" + countText, pngX + 18 + 4, pngY + 18 + 1 - 8, CONC_TEXT_COLOR, false);
    }

    /**
     * 序列卡（DNA/mRNA 输出，加宽 104）：序列号（position/total）+ 四色碱基
     * 末端窗口 + 聚合酶标记（编码中）
     */
    private void drawDnaCard(GuiGraphics graphics, int cardX, int cardY, int cardW, int cardH, Slot slot) {
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, CARD_COLOR);
        int pngX = cardX + SequenceMachineMenu.SLOT_PNG_X;
        int pngY = cardY + SequenceMachineMenu.SLOT_PNG_Y;
        graphics.blit(SLOT_TEX, pngX, pngY, 0, 0, 18, 18, 18, 18);
        int textX = pngX + 18 + 4;
        // 第一行：DNA + 序列号（position/total，代替 x数量）
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        graphics.drawString(font, "DNA", textX, pngY, NAME_COLOR, false);
        graphics.drawString(font, position + "/" + total, textX + 30, pngY, CONC_TEXT_COLOR, false);
        // 第二行：四色碱基末端窗口 + 聚合酶标记（U 与 T 同色黄）
        ItemStack stack = slot.getItem();
        SequenceData data = stack.get(ModDataComponents.SEQUENCE.get());
        String seq = data != null ? data.seq() : "";
        int baseX = textX;
        int baseY = pngY + 11;
        boolean encoding = total > 0 && position < total;
        if (!seq.isEmpty()) {
            int window = (cardW - 34) / 7;
            int from = Math.max(0, seq.length() - window);
            for (int i = from; i < seq.length() && baseX < cardX + cardW - 10; i++) {
                char base = seq.charAt(i);
                int color = switch (base) {
                    case 'A' -> BASE_A;
                    case 'T', 'U' -> BASE_T;
                    case 'C' -> BASE_C;
                    case 'G' -> BASE_G;
                    default -> CONC_TEXT_COLOR;
                };
                if (encoding && i == seq.length() - 1) {
                    graphics.fill(baseX - 1, baseY - 1, baseX + 7, baseY + 9, 0xFFFFFFFF);
                    color = 0xFF000000;
                }
                graphics.drawString(font, String.valueOf(base), baseX, baseY, color, false);
                baseX += 7;
            }
        }
        // 聚合酶标记（编码中显示在链末端后方）
        if (encoding && baseX < cardX + cardW - 4) {
            graphics.fill(baseX, baseY + 4, baseX + 3, baseY + 7, 0xFF00E5FF);
        }
        // 进度条（2px 高，宽 cardW-2）：序列进度（position/total 归一化）
        int barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + 1;
        int fill = total > 0 ? (int) Math.min((cardW - 2) * position / (double) total, cardW - 2) : 0;
        graphics.fill(cardX + 1, barY, cardX + 1 + cardW - 2, barY + 2, BAR_TRACK);
        if (fill > 0) {
            graphics.fill(cardX + 1, barY, cardX + 1 + fill, barY + 2, 0xFF4CAF50);
        }
    }

    /**
     * 多肽卡（翻译机输出，加宽 104）：PEP + 序列号 + 三字母残基末端窗口
     * （aa 主题色着色，最新残基白底反色）
     */
    private void drawPeptideCard(GuiGraphics graphics, int cardX, int cardY, int cardW, int cardH, Slot slot) {
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, CARD_COLOR);
        int pngX = cardX + SequenceMachineMenu.SLOT_PNG_X;
        int pngY = cardY + SequenceMachineMenu.SLOT_PNG_Y;
        graphics.blit(SLOT_TEX, pngX, pngY, 0, 0, 18, 18, 18, 18);
        int textX = pngX + 18 + 4;
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        graphics.drawString(font, "PEP", textX, pngY, NAME_COLOR, false);
        graphics.drawString(font, pos + "/" + total, textX + 30, pngY, CONC_TEXT_COLOR, false);
        ItemStack stack = slot.getItem();
        SequenceData data = stack.get(ModDataComponents.SEQUENCE.get());
        String seq = data != null ? data.seq() : "";
        int baseX = textX;
        int baseY = pngY + 11;
        boolean translating = total > 0 && pos < total;
        if (!seq.isEmpty()) {
            int window = (cardW - 34) / 24;
            int from = Math.max(0, seq.length() - window);
            for (int i = from; i < seq.length() && baseX < cardX + cardW - 26; i++) {
                char aa1 = seq.charAt(i);
                String aa3 = aa1To3(aa1);
                int color = cardTextColor(aaColor(aa1));
                if (translating && i == seq.length() - 1) {
                    graphics.fill(baseX - 1, baseY - 1, baseX + 19, baseY + 9, 0xFFFFFFFF);
                    color = 0xFF000000;
                }
                for (int bi = 0; bi < aa3.length(); bi++) {
                    graphics.drawString(font, String.valueOf(aa3.charAt(bi)),
                            baseX + bi * 6, baseY, color, false);
                }
                baseX += 24;
            }
        }
        if (translating && baseX < cardX + cardW - 4) {
            graphics.fill(baseX, baseY + 4, baseX + 3, baseY + 7, 0xFF00E5FF);
        }
        int barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + 1;
        int fill = total > 0 ? (int) Math.min((cardW - 2) * pos / (double) total, cardW - 2) : 0;
        graphics.fill(cardX + 1, barY, cardX + 1 + cardW - 2, barY + 2, BAR_TRACK);
        if (fill > 0) {
            graphics.fill(cardX + 1, barY, cardX + 1 + fill, barY + 2, 0xFF4CAF50);
        }
    }

    /** aa1 → 3 字母缩写（查规范密码子表；未知字符原样返回单字母） */
    protected static String aa1To3(char aa1) {
        for (int i = 0; i < com.github.crafteve.biocraft.central.Codec.CANONICAL_AA1.length; i++) {
            if (com.github.crafteve.biocraft.central.Codec.CANONICAL_AA1[i] == aa1) {
                return com.github.crafteve.biocraft.central.Codec.CANONICAL_AA3[i];
            }
        }
        return String.valueOf(aa1);
    }

    /** aa1 → 残基主题色（对应 aa-tRNA 物品的 substances.json 数据表色） */
    protected static int aaColor(char aa1) {
        String aa3 = aa1To3(aa1);
        var deferred = ModItems.byId("trna_" + aa3.toLowerCase());
        if (deferred != null && deferred.get() instanceof MoleculeItem mi) {
            return mi.getTintColor();
        }
        return 0xCCCCCC;
    }

    /**
     * renderSlot 覆写：所有序列机机器槽经 scissor 自绘（仿 dnaEncoder），玩家背包交 vanilla
     */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index >= menu.machineSlotCount) {
            super.renderSlot(graphics, slot);
            return;
        }
        boolean isInput = inputCards.stream().anyMatch(c -> c.containerSlot() == slot.index);
        boolean isOutput = outputCards.stream().anyMatch(c -> c.containerSlot() == slot.index);
        if (isInput) {
            int x = leftPos + SequenceMachineMenu.INPUT_SCROLL_X;
            int y = topPos + SequenceMachineMenu.INPUT_SCROLL_Y;
            graphics.enableScissor(x, y, x + SequenceMachineMenu.INPUT_SCROLL_W, y + SequenceMachineMenu.INPUT_SCROLL_H);
            renderScrollSlot(graphics, slot);
            graphics.disableScissor();
            return;
        }
        if (isOutput) {
            if (layout().outputVertical()) {
                int x = leftPos + 193;
                int y = topPos + 41;
                graphics.enableScissor(x, y, x + 56, y + 112);
                renderScrollSlot(graphics, slot);
                graphics.disableScissor();
                return;
            }
            int x = leftPos + SequenceMachineMenu.OUT_X;
            int y = topPos + SequenceMachineMenu.OUT_Y;
            graphics.enableScissor(x, y, x + SequenceMachineMenu.OUT_W, y + SequenceMachineMenu.OUT_H);
            renderScrollSlot(graphics, slot);
            graphics.disableScissor();
            return;
        }
        // 模板槽等非滚动槽（如转录仪 0 槽顶栏）交 vanilla
        super.renderSlot(graphics, slot);
    }

    /**
     * 滚动槽物品渲染（对齐酶工厂 renderSpeciesSlot）：物品图标 + 拖拽分裂
     * 预览 + 自动缩小堆叠数，包 scissor 裁剪滚动视口。
     * <p>
     * 方向 A：完整复刻 vanilla renderSlot 的 quickCraft 分支（源码
     * AbstractContainerScreen L198-217）——拖拽经过的槽位显示"放置后结果"
     * 半透明预览 + 黄色超限数字；无效槽剔除 + recalculateQuickCraftRemaining
     * 保持集合与显示同步（字段经 AT 开放，accesstransformer.cfg L3-6）。
     * <p>
     * 方向 B：悬停高亮交 vanilla renderSlotHighlight（isHighlightable=true），
     * 本方法不再自绘高亮。
     * <p>
     * 堆叠数：≤99 走 vanilla（renderItemDecorations），≥100 自动缩小字号
     * （3 位 0.75、4 位及以上 0.55），右下角锚点、z=200；序列样式输出槽
     * （DNA/mRNA/多肽）不画堆叠数（序列号代替）
     */
    private void renderScrollSlot(GuiGraphics graphics, Slot slot) {
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
            // 序列样式输出槽不画堆叠数（序列号代替）；其余槽按位数自动缩放堆叠数
            if (!isSeqStyleSlot(slot.index)) {
                renderStackCount(graphics, renderStack, slot.x, slot.y, countText);
            }
        }
        graphics.pose().popPose();
    }

    /** 该槽位是否为序列样式输出槽（DNA/mRNA/多肽卡）：堆叠数由序列号代替 */
    private boolean isSeqStyleSlot(int slotIndex) {
        return outputCards.stream().anyMatch(c -> c.containerSlot() == slotIndex
                && c.style() != SequenceSlotSpec.CardStyle.STOCK
                && c.style() != SequenceSlotSpec.CardStyle.NONE);
    }

    /**
     * 堆叠数渲染（对齐酶工厂 renderStackCount）：≤99 走 vanilla 原样
     * （renderItemDecorations），≥100 或自定义文本（quickCraft 黄色超限数字）
     * 自动缩小字号——以槽位右下角为锚点缩放绘制（3 位 0.75、4 位及以上
     * 0.55），数字始终贴右下角不溢出；z 提升 200 层盖在物品（z=100）上
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

    /** renderLabels 空实现：vanilla 标题/物品栏标识全部移除（文字全由 renderBg 自绘） */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /**
     * render 覆写：super 之后显式调用 renderTooltip——1.21.1 的
     * AbstractContainerScreen.render 本身不渲染 hoveredSlot 物品 tooltip
     * （重构移除，源码实证），各子类 Screen 必须补调（欠账 13）
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /** 折叠机程序滚动脱敏：\r\n\t 等控制字符替换为空格，连续空格合并，超长截断留可视 */
    protected static String sanitizeProgram(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32) {
                if (!lastSpace) {
                    sb.append(' ');
                    lastSpace = true;
                }
                continue;
            }
            sb.append(c);
            lastSpace = c == ' ';
        }
        // 去首尾空格
        int start = 0;
        while (start < sb.length() && sb.charAt(start) == ' ') start++;
        int end = sb.length();
        while (end > start && sb.charAt(end - 1) == ' ') end--;
        return sb.substring(start, end);
    }

    /** 物品色加深 1/5，与卡片底色亮度相近时改黑色（保证缩写可读，酶工厂同款算法） */
    protected static int cardTextColor(int rgb24) {
        int r = (rgb24 >> 16) & 0xFF;
        int g = (rgb24 >> 8) & 0xFF;
        int b = rgb24 & 0xFF;
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;
        int saturation = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
        if (luminance > 240 || (saturation < 40 && Math.abs(luminance - 198) < 10)) {
            return 0xFF000000;
        }
        return CompatRenderUtil.darkenOneFifth(rgb24);
    }
}

