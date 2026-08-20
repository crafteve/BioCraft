package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.SeqStepState;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
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
 * 序列机通用屏幕（256×256 窗口，画布 = gui_encoder.png）
 * <p>
 * 通用部分：背景贴图、玩家背包（酶工厂坐标）、顶部状态栏（机器名 + 阶段 +
 * 进度条）、左侧输入滚动卡片区（isActive=false 槽位 + AT 坐标，自绘卡片）、
 * 右下输出卡片区（DNA 产物整行卡 + ADP/PPi 并排副产物卡，含四色碱基序列
 * 预览）、深色编码区面板（编码器子类填入编辑器）。
 * 各机器 Screen 子类只负责差异（编码器 = 代码编辑器 + 按钮）。
 */
public class SequenceMachineScreen extends AbstractContainerScreen<SequenceMachineMenu> {

    protected static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath(
            BioCraft.MODID, "textures/gui/gui_encoder.png");
    protected static final ResourceLocation SLOT_TEX = ResourceLocation.fromNamespaceAndPath(
            BioCraft.MODID, "textures/gui/slot.png");

    /** 卡片底色（与酶工厂一致） */
    protected static final int CARD_COLOR = 0xFFC6C6C6;
    /** 深色编码区面板底色 */
    protected static final int EDIT_PANEL_COLOR = 0xFF1E1E22;
    /** 状态文本颜色 */
    protected static final int TEXT_MAIN = 0xFF3A3A3A;
    protected static final int TEXT_SUB = 0xFF707070;
    /** 进度条颜色 */
    protected static final int BAR_TRACK = 0xFFB0B0B0;
    protected static final int BAR_FILL = 0xFF4CAF50;
    /** DNA 四色碱基（动画 B 用） */
    protected static final int BASE_A = 0xFFE74C3C;
    protected static final int BASE_T = 0xFFF1C40F;
    protected static final int BASE_C = 0xFF3498DB;
    protected static final int BASE_G = 0xFF2ECC71;

    private static final double SCROLL_LERP = 0.35;
    private static final double SCROLL_PIXELS_PER_NOTCH = 8.0;

    /** 输入滚动卡片（编码器 5 张：dNTP×4 + ATP） */
    protected final List<InputCard> inputCards;
    private double scrollOffset;
    private double scrollTarget;

    public SequenceMachineScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SequenceMachineMenu.WINDOW_W;
        this.imageHeight = SequenceMachineMenu.WINDOW_H;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 156;
        this.inputCards = buildInputCards(menu.getKind());
    }

    /** 输入卡片（槽位 + 固定展示的物品缩写） */
    protected record InputCard(int containerSlot, String itemId) {
    }

    /** 按机器类型构建输入卡片（编码器 = 5 张；转录仪暂固定槽无滚动） */
    protected List<InputCard> buildInputCards(SequenceMachineKind kind) {
        if (kind != SequenceMachineKind.DNA_ENCODER) {
            return List.of();
        }
        List<InputCard> cards = new ArrayList<>();
        cards.add(new InputCard(0, "datp"));
        cards.add(new InputCard(1, "dttp"));
        cards.add(new InputCard(2, "dctp"));
        cards.add(new InputCard(3, "dgtp"));
        cards.add(new InputCard(4, "atp"));
        return cards;
    }

    // ------------------------------------------------------------------
    // tick：滚动插值 + 槽位坐标同步（AT 已拆 Slot.x/y final）
    // ------------------------------------------------------------------

    @Override
    public void containerTick() {
        super.containerTick();
        tickScroll();
    }

    private void tickScroll() {
        if (inputCards.isEmpty()) {
            return;
        }
        this.scrollOffset += (this.scrollTarget - this.scrollOffset) * SCROLL_LERP;
        if (Math.abs(this.scrollTarget - this.scrollOffset) < 0.5) {
            this.scrollOffset = this.scrollTarget;
        }
        int offset = (int) Math.round(scrollOffset);
        for (int i = 0; i < inputCards.size(); i++) {
            Slot slot = menu.getSlot(inputCards.get(i).containerSlot());
            slot.x = SequenceMachineMenu.INPUT_SCROLL_X + SequenceMachineMenu.SLOT_X;
            slot.y = SequenceMachineMenu.INPUT_SCROLL_Y + i * SequenceMachineMenu.CARD_STEP
                    - offset + SequenceMachineMenu.SLOT_Y;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inputHover(mouseX, mouseY)) {
            scrollInput(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean inputHover(double mouseX, double mouseY) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        return lx >= SequenceMachineMenu.INPUT_SCROLL_X
                && lx < SequenceMachineMenu.INPUT_SCROLL_X + SequenceMachineMenu.INPUT_SCROLL_W
                && ly >= SequenceMachineMenu.INPUT_SCROLL_Y
                && ly < SequenceMachineMenu.INPUT_SCROLL_Y + SequenceMachineMenu.INPUT_SCROLL_H;
    }

    private void scrollInput(double verticalAmount) {
        int maxScroll = Math.max(0, inputCards.size() * SequenceMachineMenu.CARD_STEP
                - SequenceMachineMenu.CARD_GAP - SequenceMachineMenu.INPUT_SCROLL_H);
        this.scrollTarget = Math.max(0,
                Math.min(scrollTarget - verticalAmount * SCROLL_PIXELS_PER_NOTCH, maxScroll));
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 背景贴图（256×256 全窗口）
        graphics.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        drawInputCards(graphics);
        drawOutputCards(graphics);
        drawEditPanel(graphics);
        drawProgressBar(graphics);
    }

    /** 输入滚动卡片：卡片底色 + 槽位贴图 + 物品图标 + 缩写 + 数量（scissor 裁剪视口） */
    private void drawInputCards(GuiGraphics graphics) {
        if (inputCards.isEmpty()) {
            return;
        }
        int x = leftPos + SequenceMachineMenu.INPUT_SCROLL_X;
        int y = topPos + SequenceMachineMenu.INPUT_SCROLL_Y;
        graphics.enableScissor(x, y, x + SequenceMachineMenu.INPUT_SCROLL_W, y + SequenceMachineMenu.INPUT_SCROLL_H);
        int offset = (int) Math.round(scrollOffset);
        for (int i = 0; i < inputCards.size(); i++) {
            InputCard card = inputCards.get(i);
            int cardY = y + i * SequenceMachineMenu.CARD_STEP - offset;
            graphics.fill(x, cardY, x + SequenceMachineMenu.CARD_W, cardY + SequenceMachineMenu.CARD_H, CARD_COLOR);
            // 槽位背景贴图
            int pngX = x + SequenceMachineMenu.SLOT_PNG_X;
            int pngY = cardY + SequenceMachineMenu.SLOT_PNG_Y;
            graphics.blit(SLOT_TEX, pngX, pngY, 0, 0, 18, 18, 18, 18);
            // 物品图标 + 数量（槽内容可能为空）
            Slot slot = menu.getSlot(card.containerSlot());
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, pngX + 1, pngY + 1);
                graphics.drawString(font, String.valueOf(stack.getCount()),
                        pngX + 10, pngY + 10, 0xFFFFFF, true);
            }
            // 缩写（物品色或灰色）
            MoleculeItem item = ModItems.byId(card.itemId()).get();
            int color = stack.isEmpty() ? TEXT_SUB : cardTextColor(item.getTintColor());
            graphics.drawString(font, item.getAbbreviation(),
                    pngX + 18 + 4, pngY, color, false);
        }
        graphics.disableScissor();
    }

    /** 输出卡片区（编码器）：DNA 产物整行卡 + ADP/PPi 并排副产物卡（物品图标由 vanilla 渲染） */
    protected void drawOutputCards(GuiGraphics graphics) {
        if (menu.getKind() != SequenceMachineKind.DNA_ENCODER) {
            return;
        }
        // DNA 产物卡（整行，含四色碱基序列预览——动画 B）
        drawOutputCard(graphics, SequenceMachineMenu.OUT_DNA_X, SequenceMachineMenu.OUT_DNA_Y,
                SequenceMachineMenu.OUT_DNA_W, 5, "DNA 产物", false);
        // ADP / PPi 副产物卡
        drawOutputCard(graphics, SequenceMachineMenu.OUT_SUB_X1, SequenceMachineMenu.OUT_SUB_Y,
                SequenceMachineMenu.OUT_SUB_W, 6, "ADP", true);
        drawOutputCard(graphics, SequenceMachineMenu.OUT_SUB_X2, SequenceMachineMenu.OUT_SUB_Y,
                SequenceMachineMenu.OUT_SUB_W, 7, "PPi", true);
    }

    private void drawOutputCard(GuiGraphics graphics, int cardX, int cardY, int cardW, int slotIndex,
                                String label, boolean small) {
        int x = leftPos + cardX;
        int y = topPos + cardY;
        graphics.fill(x, y, x + cardW, y + SequenceMachineMenu.CARD_H, CARD_COLOR);
        graphics.blit(SLOT_TEX, x + SequenceMachineMenu.SLOT_PNG_X, y + SequenceMachineMenu.SLOT_PNG_Y,
                0, 0, 18, 18, 18, 18);
        graphics.drawString(font, label,
                x + SequenceMachineMenu.SLOT_PNG_X + 18 + 4, y + SequenceMachineMenu.SLOT_PNG_Y,
                TEXT_MAIN, false);
        if (!small && slotIndex == 5) {
            drawDnaPreview(graphics, x + SequenceMachineMenu.SLOT_PNG_X + 18 + 4, y + 12);
        }
    }

    /**
     * 动画 B：DNA 四色碱基序列预览（显示链末端窗口，最新碱基高亮 + 聚合酶标记）
     * <p>
     * 从 DNA 产物槽读链（客户端经容器同步），只显示末端 28 个碱基；
     * 高亮 = 当前步刚加入的碱基（position 来自 ContainerData）
     */
    private void drawDnaPreview(GuiGraphics graphics, int startX, int startY) {
        Slot slot = menu.getSlot(5);
        ItemStack stack = slot.getItem();
        com.github.crafteve.biocraft.seq.SequenceData data =
                stack.get(com.github.crafteve.biocraft.init.ModDataComponents.SEQUENCE.get());
        String seq = data != null ? data.seq() : "";
        if (seq.isEmpty()) {
            return;
        }
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        boolean encoding = position < total;
        int window = 28;
        int from = Math.max(0, seq.length() - window);
        int x = startX;
        for (int i = from; i < seq.length(); i++) {
            char base = seq.charAt(i);
            int color = switch (base) {
                case 'A' -> BASE_A;
                case 'T' -> BASE_T;
                case 'C' -> BASE_C;
                case 'G' -> BASE_G;
                default -> TEXT_SUB;
            };
            // 最新碱基（编码中且是链末端）高亮为白底
            if (encoding && i == seq.length() - 1) {
                graphics.fill(x - 1, startY - 1, x + 7, startY + 9, 0xFFFFFFFF);
                color = 0xFF000000;
            }
            graphics.drawString(font, String.valueOf(base), x, startY, color, false);
            x += 7;
            if (x > leftPos + SequenceMachineMenu.OUT_DNA_X + SequenceMachineMenu.OUT_DNA_W - 12) {
                break;
            }
        }
        // 聚合酶标记（编码中显示在链末端后方）
        if (encoding && x < leftPos + SequenceMachineMenu.OUT_DNA_X + SequenceMachineMenu.OUT_DNA_W - 6) {
            graphics.fill(x, startY + 4, x + 3, startY + 7, 0xFF00E5FF);
        }
    }

    /** 深色编码区面板（编码器子类在 EDIT 区填入编辑器） */
    protected void drawEditPanel(GuiGraphics graphics) {
        int x = leftPos + SequenceMachineMenu.EDIT_X;
        int y = topPos + SequenceMachineMenu.EDIT_Y;
        graphics.fill(x, y, x + SequenceMachineMenu.EDIT_W, y + SequenceMachineMenu.EDIT_H, EDIT_PANEL_COLOR);
        graphics.fill(x, y, x + SequenceMachineMenu.EDIT_W, y + 1, 0xFF3A3A3A);
    }

    /** 顶部状态栏进度条（延伸进度） */
    private void drawProgressBar(GuiGraphics graphics) {
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int fill = total > 0 ? (int) ((imageWidth - 16) * position / (double) total) : 0;
        int barY = topPos + 17;
        graphics.fill(leftPos + 8, barY, leftPos + 8 + imageWidth - 16, barY + 3, BAR_TRACK);
        if (fill > 0) {
            graphics.fill(leftPos + 8, barY, leftPos + 8 + fill, barY + 3, BAR_FILL);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // 状态文本（机器名右侧）
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        String status = switch (SeqStepState.Stage.values()[Math.min(stage, SeqStepState.Stage.values().length - 1)]) {
            case IDLE -> "空闲 · 等待输入";
            case EXTENDING -> "延伸中 " + position + " / " + total;
            case DONE -> "完成";
        };
        graphics.drawString(font, Component.literal("§7" + status), 140, 6, 0xFFFFFF);
        // 区域标签
        graphics.drawString(font, Component.literal("§8输入"), 10, 32, 0xFFFFFF);
        graphics.drawString(font, Component.literal("§8编码"), SequenceMachineMenu.EDIT_X, 16, 0xFFFFFF);
        graphics.drawString(font, Component.literal("§8输出"), SequenceMachineMenu.OUT_DNA_X, 94, 0xFFFFFF);
    }

    /** 物品色加深 1/5，与卡片底色亮度相近时改黑色（保证缩写可读） */
    protected static int cardTextColor(int rgb24) {
        int r = (rgb24 >> 16) & 0xFF;
        int g = (rgb24 >> 8) & 0xFF;
        int b = rgb24 & 0xFF;
        int dr = (int) (r * 0.8);
        int dg = (int) (g * 0.8);
        int db = (int) (b * 0.8);
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 200 ? 0xFF000000 : (0xFF000000 | (dr << 16) | (dg << 8) | db);
    }

    /** renderSlot 覆写：滚动输入槽（isActive=false）由本类自绘，vanilla 只渲染 active 槽 */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (!slot.isActive()) {
            return;
        }
        super.renderSlot(graphics, slot);
    }
}
