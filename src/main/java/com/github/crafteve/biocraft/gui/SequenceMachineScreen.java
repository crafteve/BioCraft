package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.SeqStepState;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.compat.CompatRenderUtil;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.seq.SequenceData;
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
 * 序列机通用屏幕（256×256 窗口，画布 = gui_encoder.png）
 * <p>
 * 布局完全对齐酶工厂惯例：
 * <ul>
 *   <li>状态栏：机器名（y13，与酶工厂 displayname 同中轴）+ 状态 + 细进度条</li>
 *   <li>INPUT 标签：(9,30) 英文大写（酶工厂同定位）；输入滚动卡片区 (7,41) 56×112</li>
 *   <li>编码区面板：69,31-247,126（深色，编码器子类填编辑器）</li>
 *   <li>OUTPUT 标签 + 输出横向滚动卡片：(70,133)-(246,161)，DNA 卡加宽显示序列号
 *       （x数量 改为 序列号）+ 四色碱基，ADP/PPi 卡标准宽显示 x数量</li>
 *   <li>卡片元素照抄酶工厂：卡片底色 + slot.png + 彩色缩写 + 进度条 + x数量</li>
 *   <li>renderLabels 空实现（vanilla 标题/物品栏标识全部移除，文字全由 renderBg 自绘）</li>
 * </ul>
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
    /** 文字颜色（酶工厂 NAME_COLOR 纯黑） */
    protected static final int NAME_COLOR = 0xFF000000;
    protected static final int CONC_TEXT_COLOR = 0xFF3A3A3A;
    /** 进度条 */
    protected static final int BAR_TRACK = 0xFFB0B0B0;
    /** DNA 四色碱基（动画 B 用） */
    protected static final int BASE_A = 0xFFE74C3C;
    protected static final int BASE_T = 0xFFF1C40F;
    protected static final int BASE_C = 0xFF3498DB;
    protected static final int BASE_G = 0xFF2ECC71;

    private static final double SCROLL_LERP = 0.35;
    private static final double SCROLL_PIXELS_PER_NOTCH = 8.0;

    /** 输入卡片（纵向滚动）：槽位 + 固定展示的物品 */
    protected record InputCard(int containerSlot, String itemId) {
    }

    /** 输出卡片（横向滚动）：槽位 + 固定展示的物品 + 卡片宽 */
    protected record OutputCard(int containerSlot, String itemId, int cardWidth, boolean dna) {
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

    protected List<InputCard> buildInputCards(SequenceMachineKind kind) {
        if (kind == SequenceMachineKind.DNA_ENCODER) {
            List<InputCard> cards = new ArrayList<>();
            cards.add(new InputCard(0, "datp"));
            cards.add(new InputCard(1, "dttp"));
            cards.add(new InputCard(2, "dctp"));
            cards.add(new InputCard(3, "dgtp"));
            cards.add(new InputCard(4, "atp"));
            return cards;
        }
        if (kind == SequenceMachineKind.HELICASE) {
            List<InputCard> cards = new ArrayList<>();
            cards.add(new InputCard(0, "dna"));
            return cards;
        }
        if (kind == SequenceMachineKind.TRANSCRIBER) {
            List<InputCard> cards = new ArrayList<>();
            cards.add(new InputCard(1, "atp"));
            cards.add(new InputCard(2, "utp"));
            cards.add(new InputCard(3, "ctp"));
            cards.add(new InputCard(4, "gtp"));
            return cards;
        }
        if (kind == SequenceMachineKind.LOADER) {
            List<InputCard> cards = new ArrayList<>();
            cards.add(new InputCard(0, "trna"));
            cards.add(new InputCard(1, "glycine"));
            cards.add(new InputCard(2, "atp"));
            return cards;
        }
        if (kind == SequenceMachineKind.TRANSLATOR) {
            List<InputCard> cards = new ArrayList<>();
            cards.add(new InputCard(1, "gtp"));
            // 20 种 aa-tRNA 专槽（2..21，GTP 置顶）
            String[] trnas = {"trna_ala","trna_arg","trna_asn","trna_asp","trna_cys","trna_gln","trna_glu","trna_gly","trna_his","trna_ile","trna_leu","trna_lys","trna_met","trna_phe","trna_pro","trna_ser","trna_thr","trna_trp","trna_tyr","trna_val"};
            for (int i = 0; i < trnas.length; i++) {
                cards.add(new InputCard(2 + i, trnas[i]));
            }
            return cards;
        }
        return List.of();
    }

    protected List<OutputCard> buildOutputCards(SequenceMachineKind kind) {
        if (kind == SequenceMachineKind.DNA_ENCODER) {
            List<OutputCard> cards = new ArrayList<>();
            cards.add(new OutputCard(5, "dna", SequenceMachineMenu.OUT_CARD_DNA_W, true));
            cards.add(new OutputCard(6, "adp", SequenceMachineMenu.OUT_CARD_SUB_W, false));
            cards.add(new OutputCard(7, "ppi", SequenceMachineMenu.OUT_CARD_SUB_W, false));
            return cards;
        }
        if (kind == SequenceMachineKind.HELICASE) {
            List<OutputCard> cards = new ArrayList<>();
            cards.add(new OutputCard(1, "dna_single", 56, false));
            cards.add(new OutputCard(2, "dna_single", 56, false));
            return cards;
        }
        if (kind == SequenceMachineKind.TRANSCRIBER) {
            List<OutputCard> cards = new ArrayList<>();
            cards.add(new OutputCard(5, "mrna", SequenceMachineMenu.OUT_CARD_DNA_W, true));
            cards.add(new OutputCard(6, "adp", SequenceMachineMenu.OUT_CARD_SUB_W, false));
            cards.add(new OutputCard(7, "ppi", SequenceMachineMenu.OUT_CARD_SUB_W, false));
            return cards;
        }
        if (kind == SequenceMachineKind.LOADER) {
            List<OutputCard> cards = new ArrayList<>();
            cards.add(new OutputCard(3, "trna_ala", 56, false));
            cards.add(new OutputCard(4, "amp", 56, false));
            cards.add(new OutputCard(5, "ppi", 56, false));
            return cards;
        }
        if (kind == SequenceMachineKind.TRANSLATOR) {
            List<OutputCard> cards = new ArrayList<>();
            cards.add(new OutputCard(22, "polypeptide", 104, false));
            cards.add(new OutputCard(23, "trna", 56, false));
            cards.add(new OutputCard(24, "gdp", 56, false));
            cards.add(new OutputCard(25, "phosphate_ion", 56, false));
            return cards;
        }
        return List.of();
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
        // 输入（纵向）
        if (!inputCards.isEmpty()) {
            this.inputScrollOffset += (this.inputScrollTarget - this.inputScrollOffset) * SCROLL_LERP;
            if (Math.abs(this.inputScrollTarget - this.inputScrollOffset) < 0.5) {
                this.inputScrollOffset = this.inputScrollTarget;
            }
            int vOffset = (int) Math.round(inputScrollOffset);
            for (int i = 0; i < inputCards.size(); i++) {
                Slot slot = menu.getSlot(inputCards.get(i).containerSlot());
                slot.x = SequenceMachineMenu.INPUT_SCROLL_X + SequenceMachineMenu.SLOT_X;
                slot.y = SequenceMachineMenu.INPUT_SCROLL_Y + i * SequenceMachineMenu.CARD_STEP
                        - vOffset + SequenceMachineMenu.SLOT_Y;
            }
        }
        // 输出
        if (!outputCards.isEmpty()) {
            if (menu.getKind() == SequenceMachineKind.HELICASE || menu.getKind() == SequenceMachineKind.LOADER) {
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
            for (int i = 0; i < outputCards.size(); i++) {
                OutputCard card = outputCards.get(i);
                Slot slot = menu.getSlot(card.containerSlot());
                slot.x = cardX - hOffset + SequenceMachineMenu.SLOT_X;
                slot.y = SequenceMachineMenu.OUT_Y + SequenceMachineMenu.SLOT_Y;
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
        if (menu.getKind() == SequenceMachineKind.HELICASE || menu.getKind() == SequenceMachineKind.LOADER) {
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
    // 渲染
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 背景贴图（256×256 全窗口）
        graphics.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        drawStatusBar(graphics);
        drawInputLabel(graphics);
        drawOutputLabel(graphics);
        drawInputCards(graphics);
        drawOutputCards(graphics);
        drawEditPanel(graphics);
    }

    /** 状态栏（y 与酶工厂 displayname 同中轴 y13）：机器名 + 状态 + 细进度条 */
    private void drawStatusBar(GuiGraphics graphics) {
        // 机器名（方块显示名）
        graphics.drawString(this.font, this.title, this.leftPos + 8, this.topPos + 13, NAME_COLOR, false);
        // 状态文本
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        String status = switch (SeqStepState.Stage.values()[Math.min(stage, SeqStepState.Stage.values().length - 1)]) {
            case IDLE -> "IDLE";
            case EXTENDING -> "EXT " + position + "/" + total;
            case DONE -> "DONE";
        };
        // 状态文本右对齐（右缘 8px 边距）
        graphics.drawString(this.font, status,
                this.leftPos + imageWidth - 8 - this.font.width(status),
                this.topPos + 13, CONC_TEXT_COLOR, false);
        // 细进度条（y22-25，全宽）
        int fill = total > 0 ? (int) ((imageWidth - 16) * position / (double) total) : 0;
        graphics.fill(leftPos + 8, topPos + 22, leftPos + 8 + imageWidth - 16, topPos + 25, BAR_TRACK);
        if (fill > 0) {
            graphics.fill(leftPos + 8, topPos + 22, leftPos + 8 + fill, topPos + 25, 0xFF4CAF50);
        }
    }

    /** INPUT 标签：酶工厂同定位 (9,30)，英文大写纯黑 */
    private void drawInputLabel(GuiGraphics graphics) {
        graphics.drawString(this.font, "INPUT", this.leftPos + 9, this.topPos + 30, NAME_COLOR, false);
    }

    /** OUTPUT 标签：英文大写（输出卡片区上方） */
    private void drawOutputLabel(GuiGraphics graphics) {
        graphics.drawString(this.font, "OUTPUT", this.leftPos + SequenceMachineMenu.OUTPUT_LABEL_X,
                this.topPos + SequenceMachineMenu.OUTPUT_LABEL_Y, NAME_COLOR, false);
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

    /**
     * 输出横向滚动卡片：DNA 卡（序列号 + 四色碱基）与 ADP/PPi 卡（x数量）
     */
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
            if (card.dna()) {
                drawDnaCard(graphics, thisCardX, areaY, card.cardWidth(),
                        SequenceMachineMenu.OUT_CARD_H, slot);
            } else {
                drawStockCard(graphics, thisCardX, areaY, card.cardWidth(),
                        SequenceMachineMenu.OUT_CARD_H, card.itemId(), slot, false);
            }
            cardX += card.cardWidth() + SequenceMachineMenu.CARD_GAP;
        }
        graphics.disableScissor();
    }

    /**
     * 库存卡片（输入/输出副产物通用）：
     * 输入：count - remainder（消耗时单调递减，余量为待扣部分）；输出：count + remainder（产出时单调递增）
     */
    protected void drawStockCard(GuiGraphics graphics, int cardX, int cardY, int cardW, int cardH,
                                 String itemId, Slot slot, boolean isInput) {
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, CARD_COLOR);
        int pngX = cardX + SequenceMachineMenu.SLOT_PNG_X;
        int pngY = cardY + SequenceMachineMenu.SLOT_PNG_Y;
        graphics.blit(SLOT_TEX, pngX, pngY, 0, 0, 18, 18, 18, 18);
        ItemStack stack = slot.getItem();
        var deferred = ModItems.byId(itemId);
        MoleculeItem item = deferred != null ? deferred.get() : null;
        int tint;
        String abbr;
        if (item != null) {
            tint = item.getTintColor();
            abbr = item.getAbbreviation();
        } else if ("trna".equals(itemId)) {
            tint = 0xB0C4DE;
            abbr = "tRNA";
        } else {
            tint = 0xCCCCCC;
            abbr = itemId;
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
     * DNA 输出卡（加宽，压缩高度 23）：x数量改为序列号（position/total），
     * 并显示四色碱基末端窗口 + 聚合酶标记（动画 B）
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

    /** 深色编码区面板（编码器子类在 EDIT 区填入编辑器） */
    protected void drawEditPanel(GuiGraphics graphics) {
        int x = leftPos + SequenceMachineMenu.EDIT_X;
        int y = topPos + SequenceMachineMenu.EDIT_Y;
        graphics.fill(x, y, x + SequenceMachineMenu.EDIT_W, y + SequenceMachineMenu.EDIT_H, EDIT_PANEL_COLOR);
        graphics.fill(x, y, x + SequenceMachineMenu.EDIT_W, y + 1, 0xFF3A3A3A);
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
            if (menu.getKind() == SequenceMachineKind.HELICASE || menu.getKind() == SequenceMachineKind.LOADER) {
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
     * （3 位 0.75、4 位及以上 0.55），右下角锚点、z=200；DNA 槽（index 5）
     * 不画堆叠数（序列号代替，见 drawDnaCard）
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
            // 仅 DNA 序列槽（编码器/转录仪的槽 5）不画堆叠数（序列号代替）；
            // 其余槽（含装载机 PPi 槽 index 5）按位数自动缩放堆叠数 + 修饰符
            if (!isDnaSeqSlot(slot.index)) {
                renderStackCount(graphics, renderStack, slot.x, slot.y, countText);
            }
        }
        graphics.pose().popPose();
    }

    /**
     * 判断槽位是否为 DNA/mRNA 序列槽（槽 5）：
     * <p>
     * 编码器输出 DNA、转录仪输出 mRNA 都在槽 5，其堆叠数由 drawDnaCard 的
     * 序列号代替，渲染时跳过堆叠数/修饰符；装载机的 PPi 槽虽也是 index 5，
     * 但它是普通分子物品（非序列），必须正常显示堆叠数与物品修饰符
     */
    private boolean isDnaSeqSlot(int slotIndex) {
        SequenceMachineKind kind = menu.getKind();
        return slotIndex == 5 && (kind == SequenceMachineKind.DNA_ENCODER || kind == SequenceMachineKind.TRANSCRIBER);
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
